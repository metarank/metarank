package ai.metarank.main.command.util

import cats.effect.IO
import cats.effect.kernel.Resource

import java.io.{FileOutputStream, OutputStream}
import java.nio.file.Path

object StreamResource {
  def of(path: Path): Resource[IO, OutputStream] =
    Resource.make(IO(new FileOutputStream(path.toFile)))(s => IO(s.close()))
}
