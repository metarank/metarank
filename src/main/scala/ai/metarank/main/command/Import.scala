package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.flow.{ClickthroughImpressionFlow, FeatureValueFlow, FeatureValueSink, OrderCheckFlow}
import ai.metarank.fstore.Persistence
import ai.metarank.main.CliArgs.ImportArgs
import ai.metarank.model.Event
import ai.metarank.source.FileEventSource
import cats.effect.IO
import cats.effect.kernel.Resource

object Import {
  def run(
      conf: Config,
      storeResource: Resource[IO, Persistence],
      mapping: FeatureMapping,
      args: ImportArgs
  ): IO[Unit] = {
    storeResource.use(store => slurp(store, mapping, args))
  }

  def slurp(store: Persistence, mapping: FeatureMapping, args: ImportArgs): IO[Unit] = {
    slurp(FileEventSource(FileInputConfig(args.data.toString, args.offset, args.format)).stream, store, mapping)
  }

  def slurp(source: fs2.Stream[IO, Event], store: Persistence, mapping: FeatureMapping): IO[Unit] = {
    val ct    = ClickthroughImpressionFlow(store, mapping)
    val event = FeatureValueFlow(mapping, store)
    val sink  = FeatureValueSink(store)
    source
      .through(OrderCheckFlow.process)
      .through(ai.metarank.flow.PrintProgress.tap)
      .through(ct.process)
      .through(event.process)
      .through(sink.write)
      .compile
      .drain

  }

}
