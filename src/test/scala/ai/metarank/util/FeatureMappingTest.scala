package ai.metarank.util

import ai.metarank.FeatureMapping
import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.ModelConfig.ModelBackend.XGBoostBackend
import ai.metarank.feature.InteractionCountFeature.InteractionCountSchema
import ai.metarank.feature.WindowInteractionCountFeature.WindowInteractionCountSchema
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.rank.LambdaMARTModel
import cats.data.NonEmptyList
import io.github.metarank.ltrlib.model.Feature.VectorFeature
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class FeatureMappingTest extends AnyFlatSpec with Matchers {
  it should "use vector features for single-bucket counters" in {
    val mapping = FeatureMapping.fromFeatureSchema(
      schema = NonEmptyList.one(
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
          backend = XGBoostBackend(),
          features = NonEmptyList.one(FeatureName("clicks")),
          weights = Map("click" -> 1)
        )
      )
    )
    val datasetFeatures = mapping.models.values.flatMap(_.datasetDescriptor.features).toList
    datasetFeatures shouldBe List(VectorFeature("clicks", 1))
  }
}
