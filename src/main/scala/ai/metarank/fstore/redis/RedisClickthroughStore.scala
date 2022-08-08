package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence.{ClickthroughStore, KVCodec}
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp.LPUSH
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.fstore.redis.client.RedisClient.ScanCursor
import ai.metarank.model.{Clickthrough, Event, EventId}
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.{Chunk, Stream}
import cats.implicits._

case class RedisClickthroughStore(client: RedisClient) extends ClickthroughStore with Logging {
  val BATCH_SIZE                          = 128
  implicit val ctc: KVCodec[Clickthrough] = KVCodec.jsonCodec[Clickthrough]

  override def get(id: EventId): IO[Option[Clickthrough]] =
    client.get(id.value).flatMap {
      case None       => debug(s"missing ranking event for id $id") *> IO.pure(None)
      case Some(json) => IO.fromEither(ctc.decode(json)).map(Option.apply)
    }

  override def put(event: Clickthrough): IO[Unit] =
    client.set(event.ranking.id.value, ctc.encode(event)).void

  override def getall(): fs2.Stream[IO, Clickthrough] = Stream
    .unfoldLoopEval[IO, String, List[Clickthrough]]("0")(cursor =>
      for {
        scanned <- client.scan(cursor, BATCH_SIZE)
        values  <- client.mget(scanned.keys)
        decoded <- values.toList
          .map { case (k, v) => IO.fromEither(ctc.decode(v).map(v => k -> v)) }
          .sequence
          .map(_.map(_._2))
      } yield {
        scanned.cursor match {
          case "0"  => decoded -> None
          case next => decoded -> Some(next)
        }
      }
    )
    .flatMap(batch => Stream.emits(batch))
}
