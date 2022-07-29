package ai.metarank.source

import ai.metarank.config.InputConfig.{KafkaInputConfig, SourceOffset}
import ai.metarank.model.Event
import ai.metarank.source.KafkaSource.Consumer
import ai.metarank.util.Logging
import cats.effect.IO
import com.google.common.collect.Lists
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRebalanceListener, KafkaConsumer}
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import fs2.{Chunk, Stream}
import io.findify.featury.model.Timestamp
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.util
import scala.jdk.CollectionConverters._
import java.util.{Collections, Properties}

case class KafkaSource(conf: KafkaInputConfig) extends EventSource {
  val POLL_FREQUENCY = Duration.ofMillis(100)
  override def stream: Stream[IO, Event] = Stream
    .bracket(Consumer.create(conf))(_.close())
    .flatMap(consumer =>
      Stream
        .unfoldChunk[IO, Consumer, Array[Byte]](consumer)(cons => {
          val records = cons.client.poll(POLL_FREQUENCY)
          val chunk   = Chunk.seq(records.iterator().asScala.map(_.value()).toSeq)
          Some(chunk -> cons)
        })
        .flatMap(record => Stream.emits(record).through(conf.format.parse))
    )
}

object KafkaSource {
  val KAFKA_TIMEOUT = Duration.ofSeconds(10)

  case class Consumer(client: KafkaConsumer[Array[Byte], Array[Byte]]) {
    def close() = IO(client.close())
  }

  object Consumer extends Logging {
    def create(config: KafkaInputConfig) = {
      val props = new Properties()
      props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.brokers.toList.mkString(","))
      props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, config.groupId)
      config.options.getOrElse(Map.empty).foreach { case (key, value) => props.setProperty(key, value) }
      for {
        client     <- IO(new KafkaConsumer(props, new ByteArrayDeserializer(), new ByteArrayDeserializer()))
        _          <- IO(logger.info(s"created kafka consumer, broker=${config.brokers} group=${config.groupId}"))
        _          <- IO.whenA(config.options.nonEmpty)(info(s"kafka conf overrides: ${config.options}"))
        partitions <- client.partitions(config.topic)
        _          <- info(s"discovered partitions: $partitions")
        _          <- IO(client.subscribe(config.topic, config.offset))
        _          <- IO(logger.info(s"subscribed to topic ${config.topic}"))
      } yield {
        new Consumer(client)
      }
    }

    implicit class ConsumerOps(client: KafkaConsumer[Array[Byte], Array[Byte]]) {
      def subscribe(topic: String, offset: Option[SourceOffset]) =
        client.subscribe(
          Collections.singleton(topic),
          new ConsumerRebalanceListener {
            override def onPartitionsRevoked(partitions: util.Collection[TopicPartition]): Unit = {}

            override def onPartitionsAssigned(partitions: util.Collection[TopicPartition]): Unit = {
              logger.info(s"assigned partitions $partitions")
              offset match {
                case None =>
                  logger.info(s"using committed offsets for topic $topic")
                case Some(SourceOffset.Latest) =>
                  logger.info(s"using latest offsets for topic $topic")
                  client.seek2(client.endOffsets2(partitions.asScala.toList))
                case Some(SourceOffset.Earliest) =>
                  logger.info(s"using earliest offsets for topic $topic")
                  client.seek2(client.beginningOffsets2(partitions.asScala.toList))
                case Some(SourceOffset.ExactTimestamp(ts)) =>
                  logger.info(s"using offset for ts=${ts} for topic $topic")
                  client.seek2(client.offsetsForTimes2(partitions.asScala.toList, ts))
                case Some(SourceOffset.RelativeDuration(duration)) =>
                  logger.info(s"using offset for duration=$duration for topic $topic")
                  client.seek2(client.offsetsForTimes2(partitions.asScala.toList, Timestamp.now.minus(duration).ts))
              }
            }
          }
        )
      def endOffsets2(topicPartitions: List[TopicPartition]): Map[TopicPartition, Long] =
        client
          .endOffsets(Lists.newArrayList(topicPartitions: _*), KAFKA_TIMEOUT)
          .asScala
          .map { case (tp, off) => tp -> off.longValue() }
          .toMap

      def beginningOffsets2(topicPartitions: List[TopicPartition]): Map[TopicPartition, Long] =
        client
          .beginningOffsets(Lists.newArrayList(topicPartitions: _*), KAFKA_TIMEOUT)
          .asScala
          .map { case (tp, off) => tp -> off.longValue() }
          .toMap

      def offsetsForTimes2(topicPartitions: List[TopicPartition], ts: Long): Map[TopicPartition, Long] = {
        val timestamps = topicPartitions.map(tp => tp -> java.lang.Long.valueOf(ts)).toMap.asJava
        client
          .offsetsForTimes(timestamps, KAFKA_TIMEOUT)
          .asScala
          .map { case (tp, om) => tp -> om.offset().longValue }
          .toMap
      }

      def seek2(offsets: Map[TopicPartition, Long]) = offsets.foreach {
        case (tp, offset) => {
          client.seek(tp, offset)
          logger.info(s"seek ${tp.topic()}:${tp.partition()}:$offset")
        }
      }

      def partitions(topic: String): IO[List[TopicPartition]] =
        IO(client.partitionsFor(topic).asScala.map(pi => new TopicPartition(pi.topic(), pi.partition())).toList)
    }
  }
}
