package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence.KVCodec
import ai.metarank.fstore.codec.{KCodec, StoreFormat}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.fstore.transfer.StateSink
import ai.metarank.fstore.transfer.StateSink.TransferResult
import ai.metarank.model.Feature.MapFeature
import ai.metarank.model.Feature.MapFeature.MapConfig
import ai.metarank.model.FeatureValue.MapValue
import ai.metarank.model.State.MapState
import ai.metarank.model.{Key, Scalar, Timestamp}
import ai.metarank.model.Write.PutTuple
import cats.effect.IO
import cats.implicits._

case class RedisMapFeature(config: MapConfig, client: RedisClient, prefix: String, format: StoreFormat)
    extends MapFeature {
  override def put(action: PutTuple): IO[Unit] = {
    val key = format.key.encode(prefix, action.key)
    action.value match {
      case None => client.hdel(key, List(action.mapKey)).void
      case Some(value) =>
        for {
          _ <- client.hset(key, Map(action.mapKey -> format.scalar.encode(value)))
          _ <- client.expire(key, config.ttl)
        } yield {}

    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[MapValue]] = for {
    kv <- client.hgetAll(format.key.encode(prefix, key))
    decoded <- kv.toList.map { case (k, v) =>
      IO.fromEither(format.scalar.decode(v)).map(v => new String(k) -> v)
    }.sequence
  } yield {
    if (decoded.isEmpty) None else Some(MapValue(key, ts, decoded.toMap, config.ttl))
  }
}

object RedisMapFeature {
  implicit val mapSink: StateSink[MapState, RedisMapFeature] = new StateSink[MapState, RedisMapFeature] {
    override def sink(f: RedisMapFeature, state: fs2.Stream[IO, MapState]): IO[TransferResult] =
      state
        .evalMap(s =>
          for {
            key <- IO(f.format.key.encode(f.prefix, s.key))
            _   <- f.client.hset(key, s.values.map(kv => kv._1 -> f.format.scalar.encode(kv._2)))
            _   <- f.client.expire(key, f.config.ttl)
          } yield { 1 }
        )
        .compile
        .fold(0)(_ + _)
        .map(TransferResult.apply)

  }
}
