package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence.{ClickthroughStore, KVCodec}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.fstore.redis.codec.{StoreFormat, KCodec, VCodec}
import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.{Clickthrough, ClickthroughValues, Event, EventId, Identifier, ItemValue, Timestamp}
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.{Chunk, Stream}
import cats.implicits._
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.deriveCodec
import cats.implicits._

case class RedisClickthroughStore(rankings: RedisClient, prefix: String, format: StoreFormat)
    extends ClickthroughStore
    with Logging {
  val BATCH_SIZE        = 128
  val LARGE_BATCH_COUNT = 2

  override def put(cts: List[ClickthroughValues]): IO[Unit] = for {
    batches <- IO(cts.grouped(BATCH_SIZE).toList)
    _ <- IO.whenA(batches.size > LARGE_BATCH_COUNT)(warn(s"writing large batch: ${cts.size} click-throughs at once"))
    _ <- batches
      .map(batch =>
        rankings.mset(batch.map(ct => format.eventId.encode(prefix, ct.ct.id) -> format.ctv.encode(ct)).toMap)
      )
      .sequence
  } yield {}

  override def getall(): fs2.Stream[IO, ClickthroughValues] = Stream
    .unfoldLoopEval[IO, String, List[ClickthroughValues]]("0")(cursor =>
      for {
        scanned <- rankings.scan(cursor, BATCH_SIZE, s"$prefix/*")
        cts     <- rankings.mget(scanned.keys).flatMap(decodeValues)
        _       <- info(s"fetched next page of ${cts.size} clickthroughs")
      } yield {
        scanned.cursor match {
          case "0"  => cts -> None
          case next => cts -> Some(next)
        }
      }
    )
    .flatMap(batch => Stream.emits(batch))

  private def decodeValues(map: Map[String, Array[Byte]]): IO[List[ClickthroughValues]] = {
    map.toList.map { case (_, value) => IO.fromEither(format.ctv.decode(value)) }.sequence
  }
}
