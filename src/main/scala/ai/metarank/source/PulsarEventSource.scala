package ai.metarank.source

import ai.metarank.config.InputConfig.{PulsarInputConfig, SourceOffset}
import ai.metarank.config.SourceFormat
import ai.metarank.model.Event
import ai.metarank.source.PulsarEventSource.Consumer
import ai.metarank.util.Logging
import cats.effect.IO
import com.google.common.collect.Lists
import io.findify.featury.model.Timestamp
import org.apache.flink.api.common.typeinfo.TypeInformation
import io.findify.flink.api._
import org.apache.flink.util.Collector
import org.apache.pulsar.client.api.{Message, PulsarClient, SubscriptionInitialPosition, SubscriptionType}
import io.circe.parser._
import fs2.{Chunk, Stream}
import org.apache.pulsar.client.api.{Consumer => PulsarConsumer}
import scala.jdk.CollectionConverters._
import java.nio.charset.StandardCharsets

case class PulsarEventSource(conf: PulsarInputConfig)(implicit ti: TypeInformation[Event]) extends EventSource {
  override def stream: fs2.Stream[IO, Event] = Stream
    .bracket(Consumer.create(conf))(_.close())
    .flatMap(consumer => {
      Stream
        .unfoldChunkEval[IO, PulsarConsumer[Array[Byte]], Array[Byte]](consumer.consumer)(cons => {
          IO.fromCompletableFuture(IO(cons.batchReceiveAsync()))
            .flatTap(messages =>
              IO.fromCompletableFuture(
                IO(cons.acknowledgeAsync(Lists.newArrayList(messages.asScala.map(_.getMessageId).asJava)))
              ).flatMap(_ => IO.unit)
            )
            .map(messages => {
              val chunk = Chunk.seq(messages.iterator().asScala.toList.map(_.getValue))
              Some(chunk -> cons)
            })
        })
        .flatMap(message => Stream.emits(message).through(conf.format.parse))
    })
}

object PulsarEventSource {
  case class Consumer(client: PulsarClient, consumer: PulsarConsumer[Array[Byte]]) {
    def close(): IO[Unit] = IO.fromCompletableFuture(IO(client.closeAsync())).flatMap(_ => IO.unit)
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
