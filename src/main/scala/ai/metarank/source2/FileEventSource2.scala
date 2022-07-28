package ai.metarank.source2

import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.model.Event
import cats.effect.IO
import fs2.io.file.{Files, Flags, Path}

case class FileEventSource2(conf: FileInputConfig) extends EventSource2 {
  override def stream: fs2.Stream[IO, Event] =
    ??? // Files[IO].readAll(Path(conf.path.path), 1024, Flags.Read).through(fs2.text.utf8.decode[IO])
}
