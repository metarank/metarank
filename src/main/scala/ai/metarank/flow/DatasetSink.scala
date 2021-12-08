package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.flow.DatasetSink.CSVEncoderFactory
import ai.metarank.model.{Clickthrough, MValue}
import io.github.metarank.ltrlib.model.{LabeledItem, Query}
import io.github.metarank.ltrlib.output.CSVOutputFormat
import org.apache.flink.api.common.serialization.{BulkWriter, Encoder}
import org.apache.flink.connector.file.sink.FileSink
import org.apache.flink.core.fs.{FSDataOutputStream, Path}
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig

import java.nio.charset.StandardCharsets

object DatasetSink {
  def apply(mapping: FeatureMapping, path: String) =
    FileSink
      .forBulkFormat(new Path(path), CSVEncoderFactory(mapping))
      .withOutputFileConfig(new OutputFileConfig("dataset", ".csv"))
      .build()

  case class CSVWriter(stream: FSDataOutputStream, mapping: FeatureMapping) extends BulkWriter[Clickthrough] {
    override def addElement(element: Clickthrough): Unit = {
      val query = ClickthroughQuery(element.values, element.ranking.id.value, mapping.datasetDescriptor)
      val block = CSVOutputFormat.writeGroup(query).map(_.mkString(",")).mkString("", "\n", "\n")
      stream.write(block.getBytes(StandardCharsets.UTF_8))
    }

    override def flush(): Unit = {}

    override def finish(): Unit = {}
  }
  case class CSVEncoderFactory(mapping: FeatureMapping) extends BulkWriter.Factory[Clickthrough] {
    override def create(out: FSDataOutputStream): BulkWriter[Clickthrough] = {
      out.write(CSVOutputFormat.writeHeader(mapping.datasetDescriptor).mkString(",").getBytes(StandardCharsets.UTF_8))
      out.write('\n')
      CSVWriter(out, mapping)
    }
  }
}
