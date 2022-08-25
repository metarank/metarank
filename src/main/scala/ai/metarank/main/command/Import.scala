package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.flow.MetarankFlow.ProcessResult
import ai.metarank.flow.{ClickthroughImpressionFlow, FeatureValueFlow, FeatureValueSink, MetarankFlow, OrderCheckFlow}
import ai.metarank.fstore.Persistence
import ai.metarank.main.CliArgs.ImportArgs
import ai.metarank.model.Event
import ai.metarank.source.FileEventSource
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.kernel.Resource

object Import extends Logging {
  def run(
      conf: Config,
      storeResource: Resource[IO, Persistence],
      mapping: FeatureMapping,
      args: ImportArgs
  ): IO[Unit] = {
    storeResource.use(store =>
      for {
        result <- slurp(store, mapping, args)
        _      <- info(s"Imported ${result.events} in ${result.tookMillis}ms, generated ${result.updates} updates")
      } yield {}
    )
  }

  def slurp(store: Persistence, mapping: FeatureMapping, args: ImportArgs): IO[ProcessResult] = {
    slurp(FileEventSource(FileInputConfig(args.data.toString, args.offset, args.format)).stream, store, mapping)
  }

  def slurp(source: fs2.Stream[IO, Event], store: Persistence, mapping: FeatureMapping): IO[ProcessResult] = {
    MetarankFlow.process(store, source, mapping)
  }

}
