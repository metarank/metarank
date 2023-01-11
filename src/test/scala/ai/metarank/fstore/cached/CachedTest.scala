package ai.metarank.fstore.cached

import ai.metarank.fstore.FeatureSuite
import ai.metarank.model.{Feature, FeatureValue, Write}
import cats.effect.unsafe.implicits.global

trait CachedTest[W <: Write, F <: Feature[W, _ <: FeatureValue]] { this: FeatureSuite[W, _, F] =>
  def feature: F
  def write(values: List[W]): Option[FeatureValue] = {
    val f = feature()
    values.foldLeft(Option.empty[FeatureValue])((_, inc) => {
      f.put(inc).unsafeRunSync()
      f.computeValue(inc.key, inc.ts).unsafeRunSync()
    })
  }
}
