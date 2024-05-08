package ai.metarank.source

import ai.metarank.config.InputConfig.{KinesisInputConfig, SourceOffset}
import ai.metarank.model.{Event, Timestamp}
import ai.metarank.source.KinesisSource.Consumer
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.{Chunk, Stream}
import software.amazon.awssdk.http.SdkHttpConfigurationOption
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
import java.time.{Duration, Instant}
import scala.concurrent.duration._

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

  case class IteratorState(
      nextIterator: String,
      iteratorCreatedAt: Timestamp = Timestamp.now,
      millisBehind: Long = 0L,
      lastSeqNr: Option[String] = None
  ) {
    def withSeqNr(seqnr: Option[String]) = seqnr match {
      case Some(value) => copy(lastSeqNr = Some(value))
      case None        => this
    }
  }
  def shardStream(consumer: Consumer, shard: String, offset: SourceOffset): Stream[IO, Event] =
    Stream
      .eval(consumer.getShardIterator(conf.topic, shard, offset))
      .flatMap(iterator => {
        Stream
          .unfoldChunkEval[IO, IteratorState, Array[Byte]](IteratorState(iterator))(is => {
            consumer
              .getRecords(is.nextIterator)
              .handleErrorWith(ex =>
                for {
                  _ <- error("Kinesis error: ", ex)
                  _ <- error(
                    s"it=${is.nextIterator} seqnr=${is.lastSeqNr} created=${is.iteratorCreatedAt} lag=${is.millisBehind}"
                  )
                  seqnr <- IO.fromOption(is.lastSeqNr)(new Exception("cannot rewind iterator to not yet known seqnr"))
                  newIterator <- consumer.getShardIteratorOnSeqNr(conf.topic, shard, seqnr)
                  _           <- warn(s"recreated shard iterator for seqnr ${seqnr}")
                  records     <- consumer.getRecords(newIterator)
                } yield {
                  records
                }
              )
              .flatTap(records => IO.whenA(records.events.isEmpty)(IO.sleep(conf.sleepOnEmptyPeriod)))
              .map(records => {
                val chunk = Chunk.from(records.events)
                val next  = records.nextIterator
                Some(
                  chunk,
                  is.copy(
                    nextIterator = next,
                    iteratorCreatedAt = records.iteratorCreatedAt,
                    millisBehind = records.millisBehind
                  ).withSeqNr(records.lastSequenceNumber)
                )
              })
          })
          .metered(conf.getRecordsPeriod)
          .flatMap(bytes => Stream.emits(bytes).through(conf.format.parse))
      })
}

object KinesisSource {
  case class Records(
      events: List[Array[Byte]],
      nextIterator: String,
      iteratorCreatedAt: Timestamp,
      lastSequenceNumber: Option[String],
      millisBehind: Long
  )
  case class Consumer(client: KinesisAsyncClient, shards: List[String]) extends Logging {
    def close(): IO[Unit] = IO(client.close())

    def getShardIteratorOnSeqNr(topic: String, shard: String, seqnr: String): IO[String] = {
      val builder = GetShardIteratorRequest.builder().streamName(topic).shardId(shard)
      val request =
        builder.shardIteratorType(ShardIteratorType.AT_SEQUENCE_NUMBER).startingSequenceNumber(seqnr).build()
      IO.fromCompletableFuture(IO(client.getShardIterator(request)))
        .map(_.shardIterator())
        .flatTap(it => warn(s"rewind shard iterator: $it (seqnr=$seqnr)"))
    }

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
      IO.fromCompletableFuture(IO(client.getShardIterator(request)))
        .map(_.shardIterator())
        .flatTap(it => info(s"initial shard iterator: $it"))
    }

    def getRecords(it: String): IO[Records] = {
      IO
        .fromCompletableFuture(IO(client.getRecords(GetRecordsRequest.builder().shardIterator(it).build())))
        .map(response => {
          val events = response.records().asScala.toList
          val it     = response.nextShardIterator()
          val seqnr  = events.map(_.sequenceNumber()).lastOption
          logger.debug(s"it=$it seqnr=$seqnr")
          Records(
            events = events.map(_.data().asByteArray()),
            nextIterator = it,
            iteratorCreatedAt = Timestamp.now,
            lastSequenceNumber = seqnr,
            millisBehind = response.millisBehindLatest()
          )
        })
        .flatTap(rec => debug(s"getRecords: received ${rec.events.size} records"))
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
