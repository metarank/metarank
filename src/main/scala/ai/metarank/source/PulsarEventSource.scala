package ai.metarank.source

import ai.metarank.config.InputConfig.{PulsarInputConfig, SourceOffset}
import ai.metarank.model.Event
import ai.metarank.source.PulsarEventSource.Consumer
import ai.metarank.util.Logging
import cats.effect.IO
import com.google.common.collect.Lists
import io.findify.featury.model.Timestamp
import org.apache.pulsar.client.api.{
  MessageId,
  PulsarClient,
  SubscriptionInitialPosition,
  SubscriptionType,
  Consumer => PulsarConsumer
}
import fs2.{Chunk, Stream}

import scala.jdk.CollectionConverters._

case class PulsarEventSource(conf: PulsarInputConfig) extends EventSource {
  override def stream: fs2.Stream[IO, Event] = Stream
    .bracket(Consumer.create(conf))(_.close())
    .flatMap(consumer => {
      Stream
        .unfoldChunkEval[IO, Consumer, Array[Byte]](consumer)(cons =>
          for {
            messages <- cons.batchReceiveAsync()
            _        <- cons.acknowledgeAsync(messages.ids)
          } yield {
            Some(Chunk.seq(messages.events) -> cons)
          }
        )
        .flatMap(message => Stream.emits(message).through(conf.format.parse))
    })

}

object PulsarEventSource {
  case class Messages(events: List[Array[Byte]], ids: List[MessageId])
  case class Consumer(client: PulsarClient, consumer: PulsarConsumer[Array[Byte]]) extends Logging {
    def batchReceiveAsync(): IO[Messages] =
      IO.fromCompletableFuture(IO(consumer.batchReceiveAsync()))
        .map(_.asScala.toList)
        .map(messages => Messages(messages.map(_.getValue), messages.map(_.getMessageId)))
        .flatTap(m => debug(s"received batch of ${m.ids.size} messages"))

    def acknowledgeAsync(ids: List[MessageId]): IO[Unit] =
      IO.fromCompletableFuture(IO(consumer.acknowledgeAsync(Lists.newArrayList(ids.asJava))))
        .void
        .flatTap(_ => debug("ack sent"))

    def close(): IO[Unit] = IO.fromCompletableFuture(IO(client.closeAsync())).void
  }

  object Consumer {
    def create(conf: PulsarInputConfig): IO[Consumer] = for {
      client <- IO(PulsarClient.builder().serviceUrl(conf.serviceUrl).build())
      subscriptionType <- conf.subscriptionType match {
        case "exclusive" => IO.pure(SubscriptionType.Exclusive)
        case "shared"    => IO.pure(SubscriptionType.Shared)
        case "failover"  => IO.pure(SubscriptionType.Failover)
        case other       => IO.raiseError(new Exception(s"subscription type $other is not supported"))
      }
      consumerBuilder <- IO(
        client
          .newConsumer()
          .topic(conf.topic)
          .consumerName("metarank")
          .subscriptionName(conf.subscriptionName)
          .subscriptionType(subscriptionType)
          .properties(conf.options.getOrElse(Map.empty).asJava)
      )
      consumer <- conf.offset match {
        case None => IO.fromCompletableFuture(IO(consumerBuilder.subscribeAsync()))
        case Some(SourceOffset.Latest) =>
          IO.fromCompletableFuture(
            IO(consumerBuilder.subscriptionInitialPosition(SubscriptionInitialPosition.Latest).subscribeAsync())
          )
        case Some(SourceOffset.Earliest) =>
          IO.fromCompletableFuture(
            IO(consumerBuilder.subscriptionInitialPosition(SubscriptionInitialPosition.Earliest).subscribeAsync())
          )
        case Some(SourceOffset.ExactTimestamp(ts)) =>
          for {
            consumer <- IO.fromCompletableFuture(IO(consumerBuilder.subscribeAsync()))
            _        <- IO.fromCompletableFuture(IO(consumer.seekAsync(ts)))
          } yield {
            consumer
          }
        case Some(SourceOffset.RelativeDuration(duration)) =>
          for {
            consumer <- IO.fromCompletableFuture(IO(consumerBuilder.subscribeAsync()))
            _        <- IO.fromCompletableFuture(IO(consumer.seekAsync(Timestamp.now.minus(duration).ts)))
          } yield {
            consumer
          }
      }
    } yield {
      new Consumer(client, consumer)
    }
  }

}
