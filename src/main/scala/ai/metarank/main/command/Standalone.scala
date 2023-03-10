package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.flow.TrainBuffer
import ai.metarank.fstore.{TrainStore, Persistence}
import ai.metarank.main.CliArgs.{ImportArgs, StandaloneArgs}
import ai.metarank.ml.rank.LambdaMARTRanker.LambdaMARTPredictor
import ai.metarank.model.{Event, Timestamp}
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits._

object Standalone extends Logging {
  def run(
           conf: Config,
           storeResource: Resource[IO, Persistence],
           ctsResource: Resource[IO, TrainStore],
           mapping: FeatureMapping,
           args: StandaloneArgs
  ): IO[Unit] = {
    storeResource.use(store =>
      ctsResource.use(cts => {
        for {
          buffer <- prepare(conf, store, cts, mapping, args)
          _      <- IO(System.gc())
          _      <- Serve.api(store, cts, mapping, conf.api, buffer)
        } yield {}
      })
    )
  }

  def prepare(conf: Config, store: Persistence, cts: TrainStore, mapping: FeatureMapping, args: StandaloneArgs) =
    for {
      buffer <- IO(TrainBuffer(conf.core.clickthrough, store.values, cts, mapping))
      result <- Import.slurp(
        store,
        mapping,
        ImportArgs(args.conf, args.data, args.offset, args.format, args.validation, args.sort),
        conf,
        buffer
      )
      _ <- info(s"import done, flushing clickthrough queue of size=${buffer.queue.size()}")
      _ <- buffer.flushAll()
      _ <- store.sync
      _ <- cts.flush()
      _ <- info(s"Imported ${result.events} events in ${result.tookMillis}ms, generated ${result.updates} updates")
      _ <- mapping.models.toList.map { case (name, m) =>
        Train.train(store, cts, m) *> info(s"model '$name' training finished")
      }.sequence
    } yield {
      buffer
    }
}
