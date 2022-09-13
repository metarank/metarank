package ai.metarank.main.command

import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.main.CliArgs.AutoConfArgs
import ai.metarank.main.command.autofeature.EventModel
import ai.metarank.source.FileEventSource
import cats.effect.{ExitCode, IO}

object AutoConf {
  def run(args: AutoConfArgs): IO[Unit] = for {
    source <- IO(FileEventSource(FileInputConfig(args.data.toString, args.offset, args.format)).stream)
    model  <- source.compile.fold(EventModel())((model, event) => model.refresh(event))
  } yield {
    val m = model
  }
}
