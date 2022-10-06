package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence.KVCodec
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.fstore.redis.encode.KCodec
import ai.metarank.model.Feature.MapFeature
import ai.metarank.model.Feature.MapFeature.MapConfig
import ai.metarank.model.FeatureValue.MapValue
import ai.metarank.model.{Key, Scalar, Timestamp}
import ai.metarank.model.Write.PutTuple
import cats.effect.IO
import cats.implicits._

case class RedisMapFeature(config: MapConfig, client: RedisClient, prefix: String)(implicit
                                                                                   ke: KCodec[Key],
                                                                                   sc: KVCodec[Scalar]
) extends MapFeature {
  override def put(action: PutTuple): IO[Unit] = {
    val key = ke.encode(prefix, action.key)
    action.value match {
      case None => client.hdel(key, List(action.mapKey)).void
      case Some(value) =>
        client.hset(key, Map(action.mapKey -> sc.encode(value))).void
    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[MapValue]] = for {
    kv      <- client.hgetAll(ke.encode(prefix, key))
    decoded <- kv.toList.map { case (k, v) => IO.fromEither(sc.decode(v)).map(v => new String(k) -> v) }.sequence
  } yield {
    if (decoded.isEmpty) None else Some(MapValue(key, ts, decoded.toMap))
  }
}
