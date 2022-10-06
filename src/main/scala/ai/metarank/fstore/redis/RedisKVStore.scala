package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence.{KVCodec, KVStore}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.fstore.redis.encode.{KCodec, VCodec}
import ai.metarank.util.Logging
import cats.effect.IO
import cats.implicits._

case class RedisKVStore[K, V](client: RedisClient, prefix: String)(implicit
                                                                   ke: KCodec[K],
                                                                   vc: VCodec[V]
) extends KVStore[K, V]
    with Logging {

  override def get(keys: List[K]): IO[Map[K, V]] = for {
    response <- client.mget(keys.map(k => ke.encode(prefix, k)))
    decoded  <- response.toList.map { case (k, v) => decodeTuple(k, v) }.sequence
    _        <- IO(debug(s"requested ${keys.size} keys: records=${decoded.size}"))
  } yield {
    decoded.toMap
  }

  def decodeTuple(key: Array[Byte], value: Array[Byte]): IO[(K, V)] = for {
    decodedKey   <- IO.fromEither(ke.decode(key.drop(prefix.length + 1)))
    decodedValue <- IO.fromEither(vc.decode(value))
  } yield {
    decodedKey -> decodedValue
  }

  override def put(values: Map[K, V]): IO[Unit] = {
    client.mset(values.map { case (k, v) => RedisKey(prefix, kc.encode(k)).bytes -> vc.encode(v) }).void

  }
}
