package ai.metarank.source

import ai.metarank.config.InputConfig.{KinesisInputConfig, SourceOffset}
import ai.metarank.config.SourceFormat
import ai.metarank.model.Event
import ai.metarank.util.Logging
import cats.effect.IO

case class KinesisSource(conf: KinesisInputConfig) extends EventSource with Logging {
  import ai.metarank.flow.DataStreamOps._
  override def stream: fs2.Stream[IO, Event] = ???
//  override def eventStream(env: StreamExecutionEnvironment, bounded: Boolean)(implicit
//      ti: TypeInformation[Event]
//  ): DataStream[Event] = {
//    val props = new Properties()
//    props.put(AWSConfigConstants.AWS_REGION, conf.region)
//    props.put(AWSConfigConstants.AWS_CREDENTIALS_PROVIDER, "AUTO")
//    conf.offset match {
//      case SourceOffset.Latest   => props.put(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "LATEST")
//      case SourceOffset.Earliest => props.put(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "TRIM_HORIZON")
//      case SourceOffset.ExactTimestamp(ts) =>
//        props.put(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "AT_TIMESTAMP")
//        props.put(ConsumerConfigConstants.STREAM_INITIAL_TIMESTAMP, (ts / 1000.0).toString)
//      case SourceOffset.RelativeDuration(duration) =>
//        props.put(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "AT_TIMESTAMP")
//        props.put(
//          ConsumerConfigConstants.STREAM_INITIAL_TIMESTAMP,
//          (Timestamp.now.minus(duration).ts / 1000.0).toString
//        )
//    }
//    conf.options match {
//      case Some(options) =>
//        logger.info("AWS Kinesis Connector has option overrides")
//        options.foreach { case (key, value) =>
//          logger.info(s"'$key' = '$value'")
//          props.put(key, value)
//        }
//      case None => //
//    }
//    val consumer = new FlinkKinesisConsumer(conf.topic, KinesisEventSchema(conf.format, ti), props)
//    env
//      .addSource(consumer)
//      .id("kinesis-source")
//      .assignTimestampsAndWatermarks(EventWatermarkStrategy())
//      .id("kinesis-watermarks")
//  }
}

object KinesisSource {
  case class Consumer()
}
