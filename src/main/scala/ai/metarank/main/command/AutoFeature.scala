package ai.metarank.main.command

import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.main.CliArgs.AutoFeatureArgs
import ai.metarank.main.command.autofeature.{ConfigMirror, EventModel}
import ai.metarank.main.command.autofeature.rules.RuleSet
import ai.metarank.model.Event
import ai.metarank.source.FileEventSource
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.Stream
import io.circe.syntax._
import io.circe.yaml.Printer

import java.io.FileOutputStream

object AutoFeature extends Logging {
  val yamlFormat = Printer.spaces2.copy(dropNullKeys = true)

  def run(args: AutoFeatureArgs): IO[Unit] = for {
    _      <- info("Generating config file")
    source <- IO(FileEventSource(FileInputConfig(args.data.toString, args.offset, args.format)).stream)
    conf   <- run(source, args.rules.create(args))
    yaml   <- IO(yamlFormat.pretty(conf.asJson))
  } yield {
    val file   = args.out.toFile
    val stream = new FileOutputStream(file)
    stream.write(yaml.getBytes())
    stream.close()
    logger.info(s"Wrote a reference config file to ${args.out.toString}")
  }

  def run(source: Stream[IO, Event], rules: RuleSet): IO[ConfigMirror] = for {
    start <- IO(System.currentTimeMillis())
    model <- source.compile.fold(EventModel())((model, event) => {
      if (model.eventCount % 12345 == 1) {
        val rate = math.round(model.eventCount / ((System.currentTimeMillis() - start) / 1000.0))
        logger.info(s"processed ${model.eventCount} events, $rate events/s")
      }
      model.refresh(event)
    })
    _    <- info("Event model statistics collected")
    conf <- ConfigMirror.create(model, rules)
  } yield {
    conf
  }
}
