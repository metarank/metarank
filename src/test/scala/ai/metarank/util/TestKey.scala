package ai.metarank.util

import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Key
import ai.metarank.model.Key.{Env, FeatureName, Scope, Tag}

import scala.util.Random

object TestKey {
  def apply(c: FeatureConfig, id: String) = {
    Key(
      tag = Tag(c.scope, id),
      name = c.name,
      env = Env("1")
    )
  }
  def apply(
      scope: String = "product",
      fname: String = "f" + Random.nextInt(1000),
      tenant: Int = 1,
      id: String = "p1"
  ) = Key(
    tag = Tag(Scope(scope), id),
    name = FeatureName(fname),
    env = Env(tenant.toString)
  )
}
