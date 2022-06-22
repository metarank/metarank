package ai.metarank.source

import FileEventSource.EventStreamFormat
import ai.metarank.config.EventSourceConfig.{FileSourceConfig, SourceOffset}
import ai.metarank.config.{MPath, SourceFormat}
import ai.metarank.model.Event
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.connector.file.src.FileSource
import org.apache.flink.connector.file.src.reader.StreamFormat
import org.apache.flink.connector.file.src.util.CheckpointedPosition
import org.apache.flink.core.fs.{FSDataInputStream, Path}
import io.circe.parser._

import java.io.{BufferedInputStream, ByteArrayOutputStream, InputStream}
import ai.metarank.flow.DataStreamOps._
import ai.metarank.util.Logging
import io.findify.featury.model.Timestamp
import org.apache.flink.connector.file.src.compression.StandardDeCompressors
import org.apache.flink.connector.file.src.enumerate.NonSplittingRecursiveEnumerator
import io.findify.flink.api._

import scala.jdk.CollectionConverters._

case class FileEventSource(conf: FileSourceConfig) extends EventSource with Logging {
  val compressedExts = StandardDeCompressors.getCommonSuffixes.asScala.toList.map(ext => s".$ext")
  val commonExts     = List(".json", ".jsonl")

  def selectFile(path: Path): Boolean = {
    val fs     = path.getFileSystem
    val status = fs.getFileStatus(path)
    val isDir  = status.isDir
    if (isDir) {
      logger.info(s"$path is directory, doing recursive listing.")
      true
    } else {
      val name         = path.getName
      val isMatchedExt = (commonExts ++ compressedExts).exists(ext => name.endsWith(ext))
      val isTimePeriodMatch = conf.offset match {
        case SourceOffset.Latest                     => false
        case SourceOffset.Earliest                   => true
        case SourceOffset.ExactTimestamp(ts)         => status.getModificationTime > ts
        case SourceOffset.RelativeDuration(duration) => status.getModificationTime > Timestamp.now.minus(duration).ts
      }
      logger.info(s"file $path: match ext=$isMatchedExt time=$isTimePeriodMatch")
      isMatchedExt && isTimePeriodMatch
    }
  }
  override def eventStream(env: StreamExecutionEnvironment, bounded: Boolean)(implicit
      ti: TypeInformation[Event]
  ): DataStream[Event] = {
    logger.info(s"File event source: path=${conf.path}")
    env
      .fromSource(
        source = FileSource
          .forRecordStreamFormat(EventStreamFormat(conf.format), conf.path.flinkPath)
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
  case class EventStreamFormat(format: SourceFormat)(implicit val ti: TypeInformation[Event])
      extends StreamFormat[Event] {
    override def isSplittable: Boolean = false
    override def createReader(
        config: Configuration,
        stream: FSDataInputStream,
        fileLen: Long,
        splitEnd: Long
    ): StreamFormat.Reader[Event] = EventReader(stream, format)

    override def restoreReader(
        config: Configuration,
        stream: FSDataInputStream,
        restoredOffset: Long,
        fileLen: Long,
        splitEnd: Long
    ): StreamFormat.Reader[Event] = EventReader(stream, format, restoredOffset)

    override def getProducedType: TypeInformation[Event] = ti
  }

  case class EventReader(raw: FSDataInputStream, stream: InputStream, format: SourceFormat)
      extends StreamFormat.Reader[Event]
      with Logging {
    override def read(): Event = {
      format.transform(stream) match {
        case Left(error) =>
          logger.error(s"cannot decode event", error)
          null
        case Right(None) =>
          null
        case Right(Some(value)) =>
          value
      }
    }

    override def close(): Unit = stream.close()

    override def getCheckpointedPosition: CheckpointedPosition = new CheckpointedPosition(raw.getPos, 0)
  }

  object EventReader {
    def apply(stream: FSDataInputStream, format: SourceFormat, offset: Long = 0L) = {
      stream.seek(offset)
      val buffered = new BufferedInputStream(stream, 1024 * 1024)
      new EventReader(stream, buffered, format)
    }
  }
}
