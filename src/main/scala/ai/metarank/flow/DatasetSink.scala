package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.flow.DatasetSink.CSVEncoderFactory
import ai.metarank.model.{Clickthrough, MValue}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.github.metarank.ltrlib.model.{DatasetDescriptor, LabeledItem, Query}
import io.github.metarank.ltrlib.output.CSVOutputFormat
import org.apache.flink.api.common.serialization.{BulkWriter, Encoder}
import org.apache.flink.connector.file.sink.FileSink
import org.apache.flink.core.fs.{FSDataOutputStream, Path}
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig

import java.io.{BufferedOutputStream, OutputStream}
import java.nio.charset.StandardCharsets
import io.circe.syntax._
import io.findify.featury.flink.format.NoCloseOutputStream

object DatasetSink {
  def csv(dataset: DatasetDescriptor, path: String) =
    FileSink
      .forBulkFormat(new Path(path), CSVEncoderFactory(dataset))
      .withOutputFileConfig(new OutputFileConfig("dataset", ".csv"))
      .build()

  def json(dataset: DatasetDescriptor, path: String) =
    FileSink
      .forBulkFormat(new Path(path), JSONEncoderFactory(dataset))
      .withOutputFileConfig(new OutputFileConfig("dataset", ".json"))
      .build()

  case class JSONEncoderFactory(dataset: DatasetDescriptor) extends BulkWriter.Factory[Clickthrough] {
    override def create(out: FSDataOutputStream): BulkWriter[Clickthrough] = new JSONWriter(
      dataset = dataset,
      stream = new BufferedOutputStream(new NoCloseOutputStream(out), 1024 * 1024)
    )
  }

  case class JSONWriter(dataset: DatasetDescriptor, stream: OutputStream) extends BulkWriter[Clickthrough] {
    override def addElement(element: Clickthrough): Unit = {
      val query = ClickthroughQuery(element.values, element.ranking.id.value, dataset)
      stream.write(query.asJson.noSpaces.getBytes(StandardCharsets.UTF_8))
      stream.write('\n')
    }

    override def flush(): Unit = stream.flush()

    override def finish(): Unit = stream.close()
  }

  implicit val queryCodec: Codec[Query] = deriveCodec

  case class CSVWriter(stream: FSDataOutputStream, dataset: DatasetDescriptor) extends BulkWriter[Clickthrough] {
    override def addElement(element: Clickthrough): Unit = {
      val query = ClickthroughQuery(element.values, element.ranking.id.value, dataset)
      val block = CSVOutputFormat.writeGroup(query).map(_.mkString(",")).mkString("", "\n", "\n")
      stream.write(block.getBytes(StandardCharsets.UTF_8))
    }

    override def flush(): Unit = {}

    override def finish(): Unit = {}
  }
  case class CSVEncoderFactory(dataset: DatasetDescriptor) extends BulkWriter.Factory[Clickthrough] {
    override def create(out: FSDataOutputStream): BulkWriter[Clickthrough] = {
      out.write(CSVOutputFormat.writeHeader(dataset).mkString(",").getBytes(StandardCharsets.UTF_8))
      out.write('\n')
      CSVWriter(out, dataset)
    }
  }

}
