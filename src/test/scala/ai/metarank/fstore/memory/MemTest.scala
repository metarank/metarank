package ai.metarank.fstore.memory

import ai.metarank.fstore.FeatureSuite
import ai.metarank.model.{Feature, FeatureValue, Write}
import cats.effect.unsafe.implicits.global

trait MemTest[W <: Write, F <: Feature[W, _ <: FeatureValue]] { this: FeatureSuite[W] =>
  def feature: F
  def write(values: List[W]): Option[FeatureValue] = {
    values.foldLeft(Option.empty[FeatureValue])((_, inc) => {
      feature.put(inc).unsafeRunSync()
      feature.computeValue(inc.key, inc.ts).unsafeRunSync()
    })
  }
}
