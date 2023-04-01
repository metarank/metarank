package ai.metarank.util

import cats.effect.IO
import org.apache.commons.lang3.SystemUtils
import fs2.io.file.Files
import fs2.io.readInputStream
import org.apache.commons.io.IOUtils

import java.io.{ByteArrayInputStream, File, FileInputStream}
import java.nio.file.Path

case class LocalCache(cacheDir: String) extends Logging {
  def getIfExists(dir: String, file: String): IO[Option[Array[Byte]]] = for {
    targetFile <- IO(new File(cacheDir + File.separator + dir + File.separator + file))
    contents <- targetFile.exists() match {
      case true  => IO(Some(IOUtils.toByteArray(new FileInputStream(targetFile))))
      case false => IO.none
    }
  } yield {
    contents
  }

  def put(dir: String, file: String, bytes: Array[Byte]): IO[Unit] = for {
    targetDir  <- IO(new File(cacheDir + File.separator + dir))
    targetFile <- IO(new File(cacheDir + File.separator + dir + File.separator + file))
    _          <- IO.whenA(!targetDir.exists())(IO(targetDir.mkdirs()))
    _          <- info(s"writing cache file $dir/$file")
    _ <- readInputStream[IO](IO(new ByteArrayInputStream(bytes)), 1024)
      .through(Files[IO].writeAll(fs2.io.file.Path(targetFile.toString)))
      .compile
      .drain
  } yield {}
}

object LocalCache extends Logging {
  def create() = {
    for {
      topDir      <- cacheDir()
      metarankDir <- IO(new File(topDir.toString + File.separator + "metarank"))
      _ <- IO.whenA(!metarankDir.exists())(
        info(s"cache dir $metarankDir is not present, creating") *> IO(metarankDir.mkdirs())
      )
      _ <- info(s"using $metarankDir as local cache dir")
    } yield {
      LocalCache(metarankDir.toString)
    }
  }

  def cacheDir() = IO {
    val fallback = Path.of(System.getProperty("java.io.tmpdir"))
    val dir = if (SystemUtils.IS_OS_WINDOWS) {
      Option(System.getenv("LOCALAPPDATA")).map(path => Path.of(path)).filter(_.toFile.exists()).getOrElse(fallback)
    } else if (SystemUtils.IS_OS_MAC) {
      Option(System.getProperty("user.home"))
        .map(home => s"$home/Library/Caches")
        .map(path => Path.of(path))
        .filter(_.toFile.exists())
        .getOrElse(fallback)
    } else if (SystemUtils.IS_OS_LINUX) {
      val default = Option(System.getProperty("user.home"))
        .map(home => s"$home/.cache")
        .map(path => Path.of(path))
        .filter(_.toFile.exists())
      Option(System.getenv("XDG_CACHE_HOME"))
        .map(path => Path.of(path))
        .filter(_.toFile.exists())
        .orElse(default)
        .getOrElse(fallback)
    } else {
      fallback
    }
    dir
  }
}
