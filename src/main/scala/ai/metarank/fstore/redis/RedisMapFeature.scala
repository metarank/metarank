package ai.metarank.fstore.redis

import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.model.Feature.MapFeature
import ai.metarank.model.Feature.MapFeature.MapConfig
import ai.metarank.model.FeatureValue.MapValue
import ai.metarank.model.{Key, Scalar, Timestamp}
import ai.metarank.model.Write.PutTuple
import cats.effect.IO
import io.circe.syntax._
import io.circe.parser._
import cats.implicits._

case class RedisMapFeature(config: MapConfig, client: RedisClient, prefix: String)
    extends MapFeature
    with RedisFeature {
  override def put(action: PutTuple): IO[Unit] = {
    action.value match {
      case None        => client.hdel(str(action.key), List(action.mapKey)).void
      case Some(value) => client.hset(str(action.key), Map(action.mapKey -> value.asJson.noSpaces)).void
    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[MapValue]] = for {
    kv      <- client.hgetAll(str(key))
    decoded <- kv.toList.map { case (k, v) => IO.fromEither(decode[Scalar](v)).map(v => k -> v) }.sequence
  } yield {
    if (decoded.isEmpty) None else Some(MapValue(key, ts, decoded.toMap))
  }
}
