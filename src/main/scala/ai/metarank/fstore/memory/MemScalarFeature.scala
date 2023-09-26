package ai.metarank.fstore.memory

import ai.metarank.model.Feature.ScalarFeature
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Write.Put
import ai.metarank.model.{Key, Scalar, Timestamp}
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import shapeless.syntax.typeable._

case class MemScalarFeature(config: ScalarConfig, cache: Cache[Key, AnyRef] = Scaffeine().build())
    extends ScalarFeature {
  override def put(action: Put): IO[Unit] = IO(cache.put(action.key, action.value))

  override def computeValue(key: Key, ts: Timestamp): IO[Option[ScalarValue]] =
    IO(cache.getIfPresent(key).flatMap(_.cast[Scalar]).map(ScalarValue(key, ts, _, config.ttl)))

}
