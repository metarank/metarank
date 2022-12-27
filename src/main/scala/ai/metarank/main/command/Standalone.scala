package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.flow.ClickthroughJoinBuffer
import ai.metarank.fstore.{ClickthroughStore, Persistence}
import ai.metarank.main.CliArgs.{ImportArgs, StandaloneArgs}
import ai.metarank.main.command.train.SplitStrategy
import ai.metarank.model.{Event, Timestamp}
import ai.metarank.rank.LambdaMARTModel
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.implicits._
object Standalone extends Logging {
  def run(
      conf: Config,
      storeResource: Resource[IO, Persistence],
      ctsResource: Resource[IO, ClickthroughStore],
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

  def prepare(conf: Config, store: Persistence, cts: ClickthroughStore, mapping: FeatureMapping, args: StandaloneArgs) =
    for {
      buffer <- IO(ClickthroughJoinBuffer(conf.core.clickthrough, store.values, cts, mapping))
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
      _ <- mapping.models.toList.map {
        case (name, m @ LambdaMARTModel(conf, _, _, _)) =>
          Train.train(store, cts, m, name, conf.backend, SplitStrategy.default) *> info(
            s"model '$name' training finished"
          )
        case (other, _) =>
          info(s"skipping model $other")
      }.sequence
    } yield {
      buffer
    }
}
