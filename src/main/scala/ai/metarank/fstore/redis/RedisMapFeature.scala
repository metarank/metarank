package ai.metarank.fstore.redis

import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp.{HDEL, HSET}
import ai.metarank.fstore.redis.client.RedisReader
import ai.metarank.model.Feature.MapFeature
import ai.metarank.model.Feature.MapFeature.MapConfig
import ai.metarank.model.FeatureValue.MapValue
import ai.metarank.model.{Key, Scalar, Timestamp}
import ai.metarank.model.Write.PutTuple
import cats.effect.IO
import cats.effect.std.Queue
import io.circe.syntax._
import io.circe.parser._
import cats.implicits._

case class RedisMapFeature(config: MapConfig, queue: Queue[IO, RedisOp], client: RedisReader) extends MapFeature {
  override def put(action: PutTuple): IO[Unit] = {
    action.value match {
      case None        => queue.offer(HDEL(action.key.asString, action.mapKey))
      case Some(value) => queue.offer(HSET(action.key.asString, action.mapKey, value.asJson.noSpaces))
    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[MapValue]] = for {
    kv      <- client.hgetAll(key.asString)
    decoded <- kv.toList.map { case (k, v) => IO.fromEither(decode[Scalar](v)).map(v => k -> v) }.sequence
  } yield {
    if (decoded.isEmpty) None else Some(MapValue(key, ts, decoded.toMap))
  }
}
