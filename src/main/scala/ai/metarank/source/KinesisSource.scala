package ai.metarank.source

import ai.metarank.config.InputConfig.{KinesisInputConfig, SourceOffset}
import ai.metarank.model.Event
import ai.metarank.source.KinesisSource.Consumer
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.{Chunk, Stream}
import io.findify.featury.model.Timestamp
import software.amazon.awssdk.core.client.builder.SdkDefaultClientBuilder
import software.amazon.awssdk.http.SdkHttpConfigurationOption
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.{
  GetRecordsRequest,
  GetShardIteratorRequest,
  ListShardsRequest,
  ShardIteratorType
}
import software.amazon.awssdk.utils.AttributeMap

import scala.jdk.CollectionConverters._
import java.net.URI
import java.time.Instant

case class KinesisSource(conf: KinesisInputConfig) extends EventSource with Logging {
  override def stream: fs2.Stream[IO, Event] =
    Stream
      .bracket(Consumer.create(conf))(_.close())
      .flatMap(consumer => {
        Stream
          .emits(consumer.shards)
          .map(shard => shardStream(consumer, shard, conf.offset))
          .reduce((a, b) => a.merge(b))
          .flatten
      })

  def shardStream(consumer: Consumer, shard: String, offset: SourceOffset): Stream[IO, Event] =
    Stream
      .eval(consumer.getShardIterator(conf.topic, shard, offset))
      .flatMap(iterator => {
        Stream
          .unfoldChunkEval[IO, String, Array[Byte]](iterator)(it => {
            consumer
              .getRecords(it)
              .map(records => {
                val chunk = Chunk.seq(records.events)
                val next  = records.next
                Some(chunk, next)
              })
          })
          .flatMap(bytes => Stream.emits(bytes).through(conf.format.parse))
      })
}

object KinesisSource {
  case class Records(events: List[Array[Byte]], next: String)
  case class Consumer(client: KinesisAsyncClient, shards: List[String]) {
    def close(): IO[Unit] = IO(client.close())

    def getShardIterator(topic: String, shard: String, offset: SourceOffset): IO[String] = {
      val builder = GetShardIteratorRequest.builder().streamName(topic).shardId(shard)
      val request = offset match {
        case SourceOffset.Latest   => builder.shardIteratorType(ShardIteratorType.LATEST).build()
        case SourceOffset.Earliest => builder.shardIteratorType(ShardIteratorType.TRIM_HORIZON).build()
        case SourceOffset.ExactTimestamp(ts) =>
          builder.shardIteratorType(ShardIteratorType.AT_TIMESTAMP).timestamp(Instant.ofEpochMilli(ts)).build()
        case SourceOffset.RelativeDuration(duration) =>
          builder
            .shardIteratorType(ShardIteratorType.AT_TIMESTAMP)
            .timestamp(Instant.ofEpochMilli(Timestamp.now.minus(duration).ts))
            .build()
      }
      IO.fromCompletableFuture(IO(client.getShardIterator(request))).map(_.shardIterator())
    }

    def getRecords(it: String): IO[Records] = {
      IO
        .fromCompletableFuture(IO(client.getRecords(GetRecordsRequest.builder().shardIterator(it).build())))
        .map(response =>
          Records(response.records().asScala.toList.map(_.data().asByteArray()), response.nextShardIterator())
        )
    }
  }

  object Consumer extends Logging {
    def create(config: KinesisInputConfig): IO[Consumer] = {
      val builder = KinesisAsyncClient
        .builder()
        .region(Region.of(config.region))
        .httpClient(
          NettyNioAsyncHttpClient
            .builder()
            .buildWithDefaults(
              AttributeMap
                .builder()
                .put(
                  SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES,
                  java.lang.Boolean.valueOf(config.skipCertVerification)
                )
                .build()
            )
        )
      for {
        client <- IO {
          config.endpoint match {
            case None           => builder.build()
            case Some(endpoint) => builder.endpointOverride(URI.create(endpoint)).build()
          }
        }
        _ <- info(s"created kinesis consumer: ${client}")
        shards <- IO
          .fromCompletableFuture(
            IO(
              client
                .listShards(ListShardsRequest.builder().streamName(config.topic).build())
            )
          )
          .map(_.shards().asScala.map(_.shardId()).toList)
        _ <- info(s"detected topic ${config.topic} shards: $shards")
      } yield {
        new Consumer(client, shards)
      }
    }
  }
}
