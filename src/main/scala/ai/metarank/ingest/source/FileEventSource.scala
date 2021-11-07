package ai.metarank.ingest.source

import ai.metarank.config.IngestConfig.FileIngestConfig
import ai.metarank.ingest.source.FileEventSource.EventStreamFormat
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

case class FileEventSource(conf: FileIngestConfig)(implicit val ti: TypeInformation[Event]) extends EventSource {
  override def source(env: StreamExecutionEnvironment): DataStream[Event] =
    env.fromSource(
      source =
        FileSource.forRecordStreamFormat(EventStreamFormat(), new Path(conf.path)).processStaticFileSet().build(),
      watermarkStrategy = EventWatermarkStrategy(),
      sourceName = "events-source"
    )
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

  case class EventReader(stream: FSDataInputStream) extends StreamFormat.Reader[Event] {
    override def read(): Event = {
      val line = readLine(stream)
      if (line != null) decode[Event](line).right.get else null
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
