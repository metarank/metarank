package me.dfdx.metarank.feature

import me.dfdx.metarank.config.Config.{FeatureConfig, FeedbackConfig, FeedbackTypeConfig, InteractionType}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FeatureRegistryConfigTest extends AnyFlatSpec with Matchers {
  it should "load tumbling window from config" in {
    val conf = FeedbackConfig(
      List(
        FeedbackTypeConfig(InteractionType("pageview"), 1, List(FeatureConfig("window_count", List(1, 2, 4), 10)))
      )
    )
    val registry = FeatureRegistry.fromConfig(conf)
    registry.global shouldBe Map(
      InteractionType("pageview") -> List(WindowCountingFeature(List(1, 2, 4), InteractionType("pageview")))
    )
  }

}
