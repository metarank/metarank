package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence.{KVCodec, KVStore}
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp.MSET
import ai.metarank.fstore.redis.client.RedisReader
import cats.effect.IO
import cats.effect.std.Queue
import cats.implicits._

case class RedisKVStore[V](prefix: String, queue: Queue[IO, RedisOp], client: RedisReader)(implicit vc: KVCodec[V])
    extends KVStore[V] {
  override def get(keys: List[String]): IO[Map[String, V]] = for {
    raw <- client.mget(keys.map(key => s"$prefix/$key"))
    decoded <- raw
      .map { case (k, vnull) =>
        Option(vnull) match {
          case None    => IO.pure(None)
          case Some(v) => IO.fromEither(vc.decode(v)).map(v => Some(removePrefix(k) -> v))
        }
      }
      .toList
      .sequence
  } yield {
    decoded.flatten.toMap
  }

  def removePrefix(key: String): String = if (key.startsWith(prefix)) key.substring(prefix.length, key.length) else key

  override def put(values: Map[String, V]): IO[Unit] =
    queue.offer(MSET(values.map { case (k, v) => s"$prefix" -> vc.encode(v) }))
}
