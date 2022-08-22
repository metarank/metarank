package ai.metarank.fstore.cached

import ai.metarank.fstore.FeatureSuite
import ai.metarank.model.{Feature, FeatureValue, Write}
import cats.effect.unsafe.implicits.global

trait CachedTest[W <: Write, F <: Feature[W, _ <: FeatureValue]] { this: FeatureSuite[W] =>
  def feature: F
  def write(values: List[W]): Option[FeatureValue] = {
    values.foldLeft(Option.empty[FeatureValue])((_, inc) => {
      feature.put(inc).unsafeRunSync()
      feature.computeValue(inc.key, inc.ts).unsafeRunSync()
    })
  }
}
