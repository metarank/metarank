package ai.metarank.source

import ai.metarank.config.InputConfig.{FileInputConfig, SourceOffset}
import ai.metarank.model.{Event, Timestamp}
import ai.metarank.util.Logging
import cats.effect.IO
import com.github.luben.zstd.ZstdInputStream
import fs2.Stream
import fs2.io.file.{Files, Flags, Path}
import fs2.io.readInputStream

import java.io.{File, FileInputStream}
import java.util.zip.GZIPInputStream

case class FileEventSource(conf: FileInputConfig) extends EventSource with Logging {
  val decompressors = List("gz", "gzip", "zst")
  val formats       = List("json", "jsonl", "tsv")

  override def stream: Stream[IO, Event] = {
    val path = new File(conf.path)
    if (path.isDirectory) {
      Stream
        .eval(info(s"path=${conf.path} is a directory, doing recursive listing"))
        .flatMap(_ => listRecursive(Path(conf.path)).filter(selectFile).flatMap(readFile).through(conf.format.parse))

    } else if (path.exists()) {
      Stream
        .eval(info(s"path=${conf.path} is a file"))
        .flatMap(_ => Stream.emit(Path(conf.path)).filter(selectFile).flatMap(readFile).through(conf.format.parse))
    } else {
      Stream.raiseError[IO](new Exception(s"input path ${conf.path} does not exist"))
    }

  }

  def listRecursive(path: Path): Stream[IO, Path] = Files[IO]
    .list(path)
    .flatMap(child =>
      Stream.eval(Files[IO].isDirectory(child)).flatMap {
        case true  => Stream.exec(IO(logger.info(s"$child is a directory"))) ++ listRecursive(child)
        case false => Stream.exec(IO(logger.info(s"found file $child"))) ++ Stream[IO, Path](child)
      }
    )

  def selectFile(path: Path): Boolean = {
    val modificationTime = java.nio.file.Files.getLastModifiedTime(path.toNioPath).toMillis
    val timeMatches = conf.offset match {
      case SourceOffset.Latest                     => false
      case SourceOffset.Earliest                   => true
      case SourceOffset.ExactTimestamp(ts)         => modificationTime > ts
      case SourceOffset.RelativeDuration(duration) => modificationTime > Timestamp.now.minus(duration).ts
    }
    val formatMatches = path.toNioPath.toString.split('.').toList.takeRight(2) match {
      case e1 :: e2 :: Nil => (formats.contains(e1) && decompressors.contains(e2)) || formats.contains(e2)
      case _               => false
    }
    val result = timeMatches && formatMatches
    logger.info(s"file $path selected=$result (timeMatch=$timeMatches formatMatch=$formatMatches)")
    result
  }

  def readFile(path: Path): Stream[IO, Byte] = {
    val name = path.toString
    path.extName match {
      case ".gz" | ".gzip" =>
        Stream
          .eval(info(s"reading file $path (with gzip decompressor)"))
          .flatMap(_ => readInputStream[IO](IO(new GZIPInputStream(new FileInputStream(name))), 1024 * 1024))
      case ".zst" =>
        Stream
          .exec(info(s"reading file $path (with zstd decompressor)"))
          .append(readInputStream[IO](IO(new ZstdInputStream(new FileInputStream(name))), 1024 * 1024))
      case other =>
        Stream
          .eval(info(s"reading file $path (no compression)"))
          .flatMap(_ => Files[IO].readAll(path, 1024 * 1024, Flags.Read))
    }
  }
}
