package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.api.routes
import ai.metarank.api.routes.inference.{BiEncoderApi, CrossEncoderApi}
import ai.metarank.api.routes.{FeedbackApi, HealthApi, MetricsApi, RankApi, RecommendApi, TrainApi}
import ai.metarank.config.{ApiConfig, Config, InputConfig}
import ai.metarank.feature.{FieldMatchBiencoderFeature, FieldMatchCrossEncoderFeature}
import ai.metarank.flow.{MetarankFlow, PrintProgress, TrainBuffer}
import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.fstore.{Persistence, TrainStore}
import ai.metarank.main.CliArgs.ServeArgs
import ai.metarank.main.Logo
import ai.metarank.ml.onnx.encoder.EncoderConfig
import ai.metarank.ml.rank.LambdaMARTRanker.LambdaMARTPredictor
import ai.metarank.ml.{Ranker, Recommender}
import ai.metarank.model.Event.RankingEvent
import ai.metarank.model.Timestamp
import ai.metarank.source.EventSource
import ai.metarank.util.Logging
import ai.metarank.util.analytics.Metrics
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits._
import com.comcast.ip4s.{Hostname, Port}
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.hotspot.DefaultExports
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import fs2.Stream
import org.http4s.Request
import org.http4s.server.middleware.{ErrorAction, Logger}

import scala.concurrent.duration._

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
  ): IO[Unit] = {
    implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]
    for {

      health     <- IO.pure(HealthApi(store).routes)
      ranker     <- IO.pure(Ranker(mapping, store))
      rank       <- IO.pure(RankApi(ranker).routes)
      _          <- maybeWarmup(mapping, store, ranker).compile.drain
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
      routes =
        health <+> rank <+> feedback <+> train <+> metricsApi <+> rec <+> inferenceCross.routes <+> inferenceEncoder.routes
      routesWithError <- IO(
        ErrorAction.httpRoutes(routes, (req: Request[IO], err: Throwable) => error(err.toString, err))
      )
      routesWithLog <- IO(
        Logger.httpRoutes(logBody = false, logHeaders = false, logAction = Some(info(_)))(routesWithError)
      )

      httpApp = Router("/" -> routesWithLog).orNotFound
      host <- IO.fromOption(Hostname.fromString(conf.host.value))(
        new Exception(s"cannot parse hostname '${conf.host.value}'")
      )
      port <- IO.fromOption(Port.fromInt(conf.port.value))(
        new Exception(s"cannot parse port '${conf.port.value}'")
      )

      api <- IO(
        EmberServerBuilder
          .default[IO]
          .withHost(host)
          .withPort(port)
          .withHttpApp(httpApp)
          .withIdleTimeout(conf.timeout)
      )
      _ <- info(Logo.raw)
      _ <- info("Starting API...")
      _ <- api.build.use(_ => IO.never)
    } yield {}
  }

  def maybeWarmup(mapping: FeatureMapping, store: Persistence, ranker: Ranker) = for {
    lmart <- Stream.emits(mapping.models.values.collect { case lm: LambdaMARTPredictor => lm }.toSeq)
    _ <- Stream.emit(lmart.config.warmup.isDefined).flatMap {
      case false => Stream.eval(info(s"API warmup disabled for model ${lmart.name}"))
      case true =>
        for {
          model        <- Stream.eval(store.models.get(ModelName(lmart.name), lmart)).flatMap(x => Stream.fromOption(x))
          warmupConfig <- Stream.fromOption(lmart.config.warmup)
          timeThreshold <- Stream.eval(IO(Timestamp.now.plus(warmupConfig.duration)))
          _ <- Stream
            .eval(info(s"warmup of model ${model.name}: config=$warmupConfig requests=${model.warmupRequests.size}"))
          request <- Stream
            .emits[IO, RankingEvent](model.warmupRequests)
            .repeat
            .through(PrintProgress.tap(Some(store), "warmup requests"))
            .takeWhile(_ => Timestamp.now.isBefore(timeThreshold))
          _ <- Stream.eval(ranker.rerank(request, lmart.name, false, silent = true))

        } yield {}
    }
  } yield {}
}
