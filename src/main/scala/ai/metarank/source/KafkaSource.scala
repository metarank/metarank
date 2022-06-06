package ai.metarank.source

import ai.metarank.config.EventSourceConfig.{KafkaSourceConfig, SourceOffset}
import ai.metarank.model.Event
import ai.metarank.util.Logging
import io.findify.featury.model.Timestamp
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema
import io.findify.flink.api._
import org.apache.flink.util.Collector
import org.apache.kafka.clients.consumer.ConsumerRecord
import io.circe.parser._

import java.nio.charset.StandardCharsets

case class KafkaSource(conf: KafkaSourceConfig)(implicit ti: TypeInformation[Event]) extends EventSource {

  override def eventStream(env: StreamExecutionEnvironment, bounded: Boolean)(implicit
      ti: TypeInformation[Event]
  ): DataStream[Event] = {
    val offset = conf.offset match {
      case SourceOffset.Latest                     => OffsetsInitializer.latest()
      case SourceOffset.Earliest                   => OffsetsInitializer.earliest()
      case SourceOffset.ExactTimestamp(ts)         => OffsetsInitializer.timestamp(ts)
      case SourceOffset.RelativeDuration(duration) => OffsetsInitializer.timestamp(Timestamp.now.minus(duration).ts)
    }
    val deserializer = new KafkaRecordDeserializationSchema[Event] {
      override def getProducedType: TypeInformation[Event] = ti

      override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]], out: Collector[Event]): Unit = {
        val json    = new String(record.value(), StandardCharsets.UTF_8)
        val decoded = decode[Event](json)
        decoded match {
          case Left(value) =>
            logger.error(s"cannot deserialize record $json: ${value.getMessage}", value)
            throw value
          case Right(value) => out.collect(value)
        }
      }
    }
    logger.info(s"KafkaSource initialized for brokers=${conf.brokers.toList.mkString(",")} topic=${conf.topic}")
    val sourceBuilder = org.apache.flink.connector.kafka.source.KafkaSource
      .builder[Event]()
      .setTopics(conf.topic)
      .setGroupId(conf.groupId)
      .setBootstrapServers(conf.brokers.toList.mkString(","))
      .setStartingOffsets(offset)
      .setDeserializer(deserializer)
      .setProperties(customProperties(conf.options))
    val source = if (bounded) sourceBuilder.setBounded(OffsetsInitializer.latest()).build() else sourceBuilder.build()
    env.fromSource(source, EventWatermarkStrategy(), "kafka-source")
  }
}
