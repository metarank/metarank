package ai.metarank.util

import ai.metarank.FeatureMapping
import ai.metarank.config.BoosterConfig.XGBoostConfig
import ai.metarank.feature.WindowInteractionCountFeature.WindowInteractionCountSchema
import ai.metarank.ml.rank.LambdaMARTRanker.{LambdaMARTConfig, LambdaMARTPredictor}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import cats.data.NonEmptyList
import io.github.metarank.ltrlib.model.Feature.VectorFeature
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class FeatureMappingTest extends AnyFlatSpec with Matchers {
  it should "use vector features for single-bucket counters" in {
    val mapping = FeatureMapping.fromFeatureSchema(
      schema = List(
        WindowInteractionCountSchema(
          name = FeatureName("clicks"),
          interaction = "click",
          scope = ItemScopeType,
          bucket = 24.hours,
          periods = List(7)
        )
      ),
      models = Map(
        "xgboost" -> LambdaMARTConfig(
          backend = XGBoostConfig(),
          features = NonEmptyList.one(FeatureName("clicks")),
          weights = Map("click" -> 1)
        )
      )
    )
    val datasetFeatures = mapping.models.values.toList.collect { case LambdaMARTPredictor(_, _, desc) =>
      desc.features
    }
    datasetFeatures.flatten shouldBe List(VectorFeature("clicks", 1))
  }
}
