package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.fstore.Persistence
import ai.metarank.main.CliArgs.{ImportArgs, StandaloneArgs}
import ai.metarank.model.Event
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
        _ <- Import.slurp(store, mapping, ImportArgs(args.conf, args.data, args.offset, args.format))
        _ <- info("Import done, training models")
        _ <- mapping.models.toList.map {
          case (name, m @ LambdaMARTModel(conf, _, _, _)) =>
            Train.train(store, m, name, conf.backend) *> info("model training finished")
          case (other, _) =>
            info(s"skipping model $other")
        }.sequence
        queue <- Queue.bounded[IO, Option[Event]](1024)
        _ <- Serve
          .serve(store, queue, mapping)
          .background
          .use(_ => {
            info("feedback processing started") *> Serve.api(store, queue, mapping, conf.api)
          })
      } yield {}
    )
  }
}
