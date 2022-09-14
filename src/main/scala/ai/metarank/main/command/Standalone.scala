package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.flow.ClickthroughJoinBuffer
import ai.metarank.fstore.Persistence
import ai.metarank.main.CliArgs.{ImportArgs, StandaloneArgs}
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
      mapping: FeatureMapping,
      args: StandaloneArgs
  ): IO[Unit] = {
    storeResource.use(store =>
      for {
        buffer <- IO(ClickthroughJoinBuffer(conf.core.clickthrough, store, mapping))
        result <- Import.slurp(
          store,
          mapping,
          ImportArgs(args.conf, args.data, args.offset, args.format, args.validation, args.sort),
          conf,
          buffer
        )
        _ <- info(s"import done, flushing clickthrough queue of size=${buffer.queue.size()}")
        _ <- buffer.flushQueue(Timestamp(Long.MaxValue))

        _ <- info(s"Imported ${result.events} events in ${result.tookMillis}ms, generated ${result.updates} updates")
        _ <- mapping.models.toList.map {
          case (name, m @ LambdaMARTModel(conf, _, _, _)) =>
            Train.train(store, m, name, conf.backend) *> info("model training finished")
          case (other, _) =>
            info(s"skipping model $other")
        }.sequence
        _ <- IO(System.gc())
        _ <- Serve.api(store, mapping, conf.api, buffer)
      } yield {}
    )
  }
}
