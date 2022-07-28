package ai.metarank.source

import ai.metarank.config.InputConfig.{KinesisInputConfig, SourceOffset}
import ai.metarank.config.SourceFormat
import ai.metarank.model.Event
import ai.metarank.source.KinesisSource.KinesisEventSchema
import ai.metarank.util.Logging
import io.findify.featury.model.Timestamp
import io.findify.flink.api.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.kinesis.shaded.org.apache.flink.connector.aws.config.AWSConfigConstants
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants
import org.apache.flink.streaming.connectors.kinesis.serialization.KinesisDeserializationSchema

import java.nio.charset.StandardCharsets
import java.util.Properties

case class KinesisSource(conf: KinesisInputConfig) extends EventSource with Logging {
  import ai.metarank.flow.DataStreamOps._
  override def eventStream(env: StreamExecutionEnvironment, bounded: Boolean)(implicit
      ti: TypeInformation[Event]
  ): DataStream[Event] = {
    val props = new Properties()
    props.put(AWSConfigConstants.AWS_REGION, conf.region)
    props.put(AWSConfigConstants.AWS_CREDENTIALS_PROVIDER, "AUTO")
    conf.offset match {
      case SourceOffset.Latest   => props.put(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "LATEST")
      case SourceOffset.Earliest => props.put(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "TRIM_HORIZON")
      case SourceOffset.ExactTimestamp(ts) =>
        props.put(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "AT_TIMESTAMP")
        props.put(ConsumerConfigConstants.STREAM_INITIAL_TIMESTAMP, (ts / 1000.0).toString)
      case SourceOffset.RelativeDuration(duration) =>
        props.put(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "AT_TIMESTAMP")
        props.put(
          ConsumerConfigConstants.STREAM_INITIAL_TIMESTAMP,
          (Timestamp.now.minus(duration).ts / 1000.0).toString
        )
    }
    conf.options match {
      case Some(options) =>
        logger.info("AWS Kinesis Connector has option overrides")
        options.foreach { case (key, value) =>
          logger.info(s"'$key' = '$value'")
          props.put(key, value)
        }
      case None => //
    }
    val consumer = new FlinkKinesisConsumer(conf.topic, KinesisEventSchema(conf.format, ti), props)
    env
      .addSource(consumer)
      .id("kinesis-source")
      .assignTimestampsAndWatermarks(EventWatermarkStrategy())
      .id("kinesis-watermarks")
  }
}

object KinesisSource {
  case class KinesisEventSchema(format: SourceFormat, ti: TypeInformation[Event])
      extends KinesisDeserializationSchema[Event]
      with Logging {
    override def deserialize(
        recordValue: Array[Byte],
        partitionKey: String,
        seqNum: String,
        approxArrivalTimestamp: Long,
        stream: String,
        shardId: String
    ): Event = {
      format.parse(recordValue) match {
        case Left(value) =>
          val string = new String(recordValue, StandardCharsets.UTF_8)
          logger.error(s"cannot parse event $string", value)
          null
        case Right(Some(value)) =>
          value
        case Right(None) =>
          null
      }
    }

    override def getProducedType: TypeInformation[Event] = ti
  }
}
