package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence.{ClickthroughStore, KVCodec}
import ai.metarank.fstore.redis.client.RedisClient
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

case class RedisClickthroughStore(rankings: RedisClient, prefix: String) extends ClickthroughStore with Logging {
  import RedisClickthroughStore._
  val BATCH_SIZE = 128

  override def put(cts: List[ClickthroughValues]): IO[Unit] = {
    cts
      .grouped(BATCH_SIZE)
      .toList
      .map(batch => rankings.mset(batch.map(ct => (prefix + "/" + ct.ct.id.value) -> ctc.encode(ct)).toMap))
      .sequence
      .void

  }

  override def getall(): fs2.Stream[IO, ClickthroughValues] = Stream
    .unfoldLoopEval[IO, String, List[ClickthroughValues]]("0")(cursor =>
      for {
        scanned <- rankings.scan(cursor, BATCH_SIZE, s"$prefix/*")
        cts     <- rankings.mget(scanned.keys).flatMap(decodeMap[ClickthroughValues](_))
        _       <- info(s"fetched next page of ${cts.size} clickthroughs")
      } yield {
        scanned.cursor match {
          case "0"  => cts.values.toList -> None
          case next => cts.values.toList -> Some(next)
        }
      }
    )
    .flatMap(batch => Stream.emits(batch))

  private def decodeMap[T](map: Map[String, String])(implicit dec: KVCodec[T]): IO[Map[String, T]] = {
    map.toList
      .map { case (key, value) =>
        IO.fromEither(dec.decode(value)).map(value => key.substring(prefix.length + 1) -> value)
      }
      .sequence
      .map(_.toMap)
  }
}

object RedisClickthroughStore {
  val ctc = KVCodec.jsonCodec[ClickthroughValues]
}
