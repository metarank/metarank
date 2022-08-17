package ai.metarank.main.command

import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.main.CliArgs.SortArgs
import ai.metarank.source.FileEventSource
import cats.effect.IO
import io.circe.syntax._
import java.io.FileOutputStream

object Sort {
  def run(args: SortArgs): IO[Unit] = for {
    events <- FileEventSource(FileInputConfig(args.data.toString, args.offset, args.format)).stream.compile.toList
    sorted <- IO(events.sortBy(_.timestamp.ts))
    out    <- IO(new FileOutputStream(args.out.toFile))
    _      <- IO(sorted.foreach(e => out.write((e.asJson.noSpaces + "\n").getBytes())))
    _      <- IO(out.close())
  } yield {
    //
  }
}
