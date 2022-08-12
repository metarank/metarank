package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.flow.{ClickthroughImpressionFlow, FeatureValueFlow, FeatureValueSink}
import ai.metarank.fstore.Persistence
import ai.metarank.main.CliArgs.ImportArgs
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
    val flowResource = for {
      store <- storeResource
      ct    = ClickthroughImpressionFlow(store, mapping)
      event = FeatureValueFlow(mapping, store)
      sink  = FeatureValueSink(store)
      flow = FileEventSource(FileInputConfig(args.data.toString, args.offset, args.format)).stream
        .through(ai.metarank.flow.PrintProgress.tap)
        .through(ct.process)
        .through(event.process)
        .through(sink.write)
    } yield {
      flow
    }
    flowResource.use(_.compile.drain)

  }
}
