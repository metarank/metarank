package ai.metarank.fstore.redis

import ai.metarank.config.StateStoreConfig.RedisStateConfig
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.KVCodec
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.{RedisPipeline, RedisReader}
import ai.metarank.model.Schema
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import scala.concurrent.duration._

case class RedisPersistence(schema: Schema, queue: Queue[IO, RedisOp], client: RedisReader, writer: RedisPipeline)
    extends Persistence {
  override lazy val counters = schema.counters.view.mapValues(RedisCounterFeature(_, queue, client)).toMap
  override lazy val periodicCounters =
    schema.periodicCounters.view.mapValues(RedisPeriodicCounterFeature(_, queue, client)).toMap
  override lazy val lists =
    schema.lists.view.mapValues(RedisBoundedListFeature(_, queue, client)).toMap
  override lazy val freqs   = schema.freqs.view.mapValues(RedisFreqEstimatorFeature(_, queue, client)).toMap
  override lazy val scalars = schema.scalars.view.mapValues(RedisScalarFeature(_, queue, client)).toMap
  override lazy val stats   = schema.stats.view.mapValues(RedisStatsEstimatorFeature(_, queue, client)).toMap
  override lazy val maps    = schema.maps.view.mapValues(RedisMapFeature(_, queue, client)).toMap

  override def kv[K: KVCodec, V: KVCodec](name: String): Persistence.KVStore[K, V] =
    RedisKVStore(name, queue, client)

  override def stream[V: Persistence.KVCodec](name: String): Persistence.StreamStore[V] =
    RedisStreamStore(name, queue, client)

  override def healthcheck(): IO[Unit] =
    client.ping().void

  override def run(): IO[Unit] =
    fs2.Stream
      .fromQueueUnterminated(queue)
      .groupWithin(128, 1.second)
      .map(chunk => writer.batch(chunk.toList))
      .compile
      .drain
}

object RedisPersistence {
  def create(schema: Schema, host: String, port: Int): Resource[IO, RedisPersistence] = for {
    client <- RedisReader.create(host, port, 0)
    writer <- RedisPipeline.create(host, port, 0)
    queue  <- Resource.liftK(Queue.unbounded[IO, RedisOp])
  } yield {
    RedisPersistence(schema, queue, client, writer)
  }

}
