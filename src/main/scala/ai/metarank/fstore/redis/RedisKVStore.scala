package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence.{KVCodec, KVStore}
import ai.metarank.fstore.codec.{KCodec, VCodec}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.fstore.transfer.StateSink
import ai.metarank.fstore.transfer.StateSink.TransferResult
import ai.metarank.model.{FeatureValue, Key}
import ai.metarank.util.Logging
import cats.effect.IO
import cats.implicits._
import scala.concurrent.duration._

case class RedisKVStore[K, V](client: RedisClient, prefix: String)(implicit
    kc: KCodec[K],
    vc: VCodec[V]
) extends KVStore[K, V]
    with Logging {

  override def get(keys: List[K]): IO[Map[K, V]] = for {
    response <- client.mget(keys.map(k => kc.encode(prefix, k)))
    decoded  <- response.toList.map { case (k, v) => decodeTuple(k, v) }.sequence
    _        <- IO(debug(s"requested ${keys.size} keys: records=${decoded.size}"))
  } yield {
    decoded.toMap
  }

  def decodeTuple(key: String, value: Array[Byte]): IO[(K, V)] = for {
    decodedKey   <- IO.fromEither(kc.decode(key))
    decodedValue <- IO.fromEither(vc.decode(value))
  } yield {
    decodedKey -> decodedValue
  }

  override def put(values: Map[K, V]): IO[Unit] = {
    client.mset(values.map { case (k, v) => kc.encode(prefix, k) -> vc.encode(v) }).void

  }
}

object RedisKVStore {
  implicit val valueSink: StateSink[FeatureValue, RedisKVStore[Key, FeatureValue]] =
    new StateSink[FeatureValue, RedisKVStore[Key, FeatureValue]] {
      override def sink(f: RedisKVStore[Key, FeatureValue], state: fs2.Stream[IO, FeatureValue]): IO[TransferResult] =
        state
          .groupWithin(128, 1.second)
          .evalMap(fvs => f.put(fvs.toList.map(fv => fv.key -> fv).toMap).map(_ => fvs.size))
          .compile
          .fold(0)(_ + _)
          .map(TransferResult.apply)

    }
}
