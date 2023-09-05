package ai.metarank.fstore.redis

import ai.metarank.flow.PrintProgress
import ai.metarank.fstore.TrainStore
import ai.metarank.fstore.Persistence.KVCodec
import ai.metarank.fstore.codec.{KCodec, StoreFormat, VCodec}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.{Clickthrough, Event, EventId, Identifier, ItemValue, Timestamp, TrainValues}
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.{Chunk, Stream}
import cats.implicits._
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.deriveCodec
import cats.implicits._

import scala.concurrent.duration.FiniteDuration

case class RedisTrainStore(rankings: RedisClient, prefix: String, format: StoreFormat, ttl: FiniteDuration)
    extends TrainStore
    with Logging {
  val BATCH_SIZE        = 128
  val LARGE_BATCH_COUNT = 2

  override def put(cts: List[TrainValues]): IO[Unit] = for {
    batches <- IO(cts.grouped(BATCH_SIZE).toList)
    _ <- IO.whenA(batches.size > LARGE_BATCH_COUNT)(warn(s"writing large batch: ${cts.size} click-throughs at once"))
    _ <- batches
      .map(batch => rankings.mset(batch.map(ct => format.eventId.encode(prefix, ct.id) -> format.ctv.encode(ct)).toMap))
      .sequence
    _ <- cts.map(ct => format.eventId.encode(prefix, ct.id)).traverse(key => rankings.expire(key, ttl))
  } yield {}

  override def flush(): IO[Unit] = rankings.doFlush(rankings.reader.ping().toCompletableFuture)

  override def getall(): fs2.Stream[IO, TrainValues] = Stream
    .unfoldLoopEval[IO, String, List[TrainValues]]("0")(cursor =>
      for {
        scanned <- rankings.scan(cursor, BATCH_SIZE, s"$prefix/*")
        cts     <- rankings.mget(scanned.keys).flatMap(decodeValues)
      } yield {
        scanned.cursor match {
          case "0"  => cts -> None
          case next => cts -> Some(next)
        }
      }
    )
    .flatMap(batch => Stream.emits(batch))
    .through(PrintProgress.tap(None, "click-throughs"))

  private def decodeValues(map: Map[String, Array[Byte]]): IO[List[TrainValues]] = {
    map.toList.map { case (_, value) => IO.fromEither(format.ctv.decode(value)) }.sequence
  }
}
