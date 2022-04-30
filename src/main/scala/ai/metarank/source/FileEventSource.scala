package ai.metarank.source

import FileEventSource.EventStreamFormat
import ai.metarank.config.MPath
import ai.metarank.model.Event
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.connector.file.src.FileSource
import org.apache.flink.connector.file.src.reader.StreamFormat
import org.apache.flink.connector.file.src.util.CheckpointedPosition
import org.apache.flink.core.fs.{FSDataInputStream, Path}
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import io.circe.parser._

import java.io.{ByteArrayOutputStream, InputStream}
import ai.metarank.flow.DataStreamOps._
import ai.metarank.util.Logging
import org.apache.flink.connector.file.src.compression.StandardDeCompressors
import org.apache.flink.connector.file.src.enumerate.NonSplittingRecursiveEnumerator

import scala.collection.JavaConverters._

case class FileEventSource(path: MPath) extends EventSource with Logging {
  val compressedExts = StandardDeCompressors.getCommonSuffixes.asScala.toList.map(ext => s".$ext")
  val commonExts     = List(".json", ".jsonl")

  def selectFile(path: Path): Boolean = {
    val fs    = path.getFileSystem
    val isDir = fs.getFileStatus(path).isDir
    if (isDir) {
      logger.info(s"$path is directory, doing recursive listing.")
      true
    } else {
      val name      = path.getName
      val isMatched = (commonExts ++ compressedExts).exists(ext => name.endsWith(ext))
      if (isMatched) {
        logger.info(s"File $path is selected as event source")
      } else {
        logger.warn(s"File $path is NOT looking like an event source, skipping")
      }
      isMatched
    }
  }
  override def eventStream(env: StreamExecutionEnvironment, bounded: Boolean)(implicit
      ti: TypeInformation[Event]
  ): DataStream[Event] = {
    logger.info(s"File event source: path=${path}")
    env
      .fromSource(
        source = FileSource
          .forRecordStreamFormat(EventStreamFormat(), path.flinkPath)
          .processStaticFileSet()
          .setFileEnumerator(() => new NonSplittingRecursiveEnumerator(selectFile))
          .build(),
        watermarkStrategy = EventWatermarkStrategy(),
        sourceName = "events-source"
      )
      .id("file-source")
  }
}

object FileEventSource {
  case class EventStreamFormat()(implicit val ti: TypeInformation[Event]) extends StreamFormat[Event] {
    override def isSplittable: Boolean = false
    override def createReader(
        config: Configuration,
        stream: FSDataInputStream,
        fileLen: Long,
        splitEnd: Long
    ): StreamFormat.Reader[Event] = EventReader(stream)

    override def restoreReader(
        config: Configuration,
        stream: FSDataInputStream,
        restoredOffset: Long,
        fileLen: Long,
        splitEnd: Long
    ): StreamFormat.Reader[Event] = EventReader(stream, restoredOffset)

    override def getProducedType: TypeInformation[Event] = ti
  }

  case class EventReader(stream: FSDataInputStream) extends StreamFormat.Reader[Event] with Logging {
    override def read(): Event = {
      val line = readLine(stream)
      if (line != null) decode[Event](line) match {
        case Left(value) =>
          logger.error(s"cannot decode line ${line}", value)
          throw new IllegalArgumentException(s"json decoding error '$value' on line '${line}'")
        case Right(value) =>
          value
      }
      else null
    }

    override def close(): Unit = stream.close()

    override def getCheckpointedPosition: CheckpointedPosition = new CheckpointedPosition(stream.getPos, 0)
  }

  object EventReader {
    def apply(stream: FSDataInputStream, offset: Long) = {
      stream.seek(offset)
      new EventReader(stream)
    }
  }

  def readLine(stream: InputStream): String = {
    val buffer = new ByteArrayOutputStream(32)
    var count  = 0
    var next   = stream.read()
    if ((next == -1) && (count == 0)) {
      null
    } else {
      while ((next != -1) && (next != '\n')) {
        buffer.write(next)
        next = stream.read()
        count += 1
      }
      new String(buffer.toByteArray)
    }

  }
}
