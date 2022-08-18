package ai.metarank.fstore.redis

import ai.metarank.fstore.FeatureSuite
import ai.metarank.model.{Feature, FeatureValue, Write}
import cats.effect.unsafe.implicits.global
import org.scalatest.BeforeAndAfterAll

trait RedisFeatureTest[W <: Write, F <: Feature[W, _ <: FeatureValue]] extends BeforeAndAfterAll with RedisTest {
  this: FeatureSuite[W] =>

  def feature: F
  def write(values: List[W]): Option[FeatureValue] = {
    values.foldLeft(Option.empty[FeatureValue])((_, inc) => {
      feature.put(inc).unsafeRunSync()
      feature.computeValue(inc.key, inc.ts).unsafeRunSync()
    })
  }
}

object RedisFeatureTest {}
