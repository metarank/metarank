package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence.{KVCodec, KVStore}
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp.{HSET, MSET}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.std.Queue
import cats.implicits._

case class RedisKVStore[K, V](prefix: String, client: RedisClient)(implicit
    kc: KVCodec[K],
    vc: KVCodec[V]
) extends KVStore[K, V]
    with Logging {
  val BATCH_SIZE = 128

  override def get(keys: List[K]): IO[Map[K, V]] = for {
    batches   <- IO(keys.grouped(BATCH_SIZE).toList)
    responses <- batches.map(keys => client.hget(prefix, keys.map(kc.encode))).sequence
    decoded   <- responses.flatten.map { case (k, v) => decodeTuple(k, v) }.sequence
    _         <- IO(debug(s"requested ${keys.size} keys: batches=${responses.size} records=${decoded.size}"))
  } yield {
    decoded.toMap
  }

  def decodeTuple(key: String, value: String): IO[(K, V)] = for {
    decodedKey   <- IO.fromEither(kc.decode(key))
    decodedValue <- IO.fromEither(vc.decode(value))
  } yield {
    decodedKey -> decodedValue
  }

  override def put(values: Map[K, V]): IO[Unit] = {
    client.hset(prefix, values.map { case (k, v) => kc.encode(k) -> vc.encode(v) }).void
  }
}
