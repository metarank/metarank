package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.api.routes
import ai.metarank.api.routes.inference.{BiEncoderApi, CrossEncoderApi}
import ai.metarank.api.routes.{FeedbackApi, HealthApi, MetricsApi, RankApi, RecommendApi, TrainApi}
import ai.metarank.config.{ApiConfig, Config, InputConfig}
import ai.metarank.feature.{FieldMatchBiencoderFeature, FieldMatchCrossEncoderFeature}
import ai.metarank.flow.{MetarankFlow, TrainBuffer}
import ai.metarank.fstore.{Persistence, TrainStore}
import ai.metarank.main.CliArgs.ServeArgs
import ai.metarank.main.Logo
import ai.metarank.ml.onnx.encoder.EncoderConfig
import ai.metarank.ml.{Ranker, Recommender}
import ai.metarank.source.EventSource
import ai.metarank.util.Logging
import ai.metarank.util.analytics.Metrics
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits._
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.hotspot.DefaultExports
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router

object Serve extends Logging {
  def run(
      conf: Config,
      storeResource: Resource[IO, Persistence],
      ctsResource: Resource[IO, TrainStore],
      mapping: FeatureMapping,
      args: ServeArgs
  ): IO[Unit] = {
    storeResource.use(store => {
      ctsResource.use(cts => {
        val buffer = TrainBuffer(conf.core.clickthrough, store.values, cts, mapping)
        conf.input match {
          case None =>
            info("no stream input defined in config, using only REST API") *> api(
              store,
              cts,
              mapping,
              conf.api,
              buffer,
              conf.inference
            )
          case Some(sourceConfig) =>
            val source = EventSource.fromConfig(sourceConfig)
            MetarankFlow
              .process(store, source.stream, mapping, buffer)
              .background
              .use(_ =>
                info(s"started ${source.conf} source") *> api(store, cts, mapping, conf.api, buffer, conf.inference)
              )
        }
      })
    })
  }

  def api(
      store: Persistence,
      cts: TrainStore,
      mapping: FeatureMapping,
      conf: ApiConfig,
      buffer: TrainBuffer,
      inference: Map[String, EncoderConfig]
  ): IO[Unit] = for {
    health     <- IO.pure(HealthApi(store).routes)
    rank       <- IO.pure(RankApi(Ranker(mapping, store)).routes)
    feedback   <- IO.pure(FeedbackApi(store, mapping, buffer).routes)
    train      <- IO.pure(TrainApi(mapping, store, cts).routes)
    rec        <- IO.pure(RecommendApi(Recommender(mapping, store), store).routes)
    metricsApi <- IO(DefaultExports.initialize()) *> IO.pure(MetricsApi().routes)
    inferenceEncoder <- BiEncoderApi.create(
      models = inference,
      existing = mapping.features.collect { case x: FieldMatchBiencoderFeature => x }
    )
    inferenceCross <- CrossEncoderApi.create(
      models = inference,
      existing = mapping.features.collect { case x: FieldMatchCrossEncoderFeature => x }
    )
    routes  = health <+> rank <+> feedback <+> train <+> metricsApi <+> rec
    httpApp = Router("/" -> routes).orNotFound
    api = BlazeServerBuilder[IO]
      .bindHttp(conf.port.value, conf.host.value)
      .withHttpApp(httpApp)
      .withBanner(Logo.lines)

    _ <- info("Starting API...")
    _ <- api.serve.compile.drain
  } yield {}
}
