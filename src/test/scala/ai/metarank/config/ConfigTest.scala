package ai.metarank.config

import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.ModelConfig.ModelBackend.XGBoostBackend
import ai.metarank.feature.StringFeature.EncoderName.IndexEncoderName
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Key.FeatureName
import ai.metarank.util.TestConfig
import better.files.File
import cats.data.{NonEmptyList, NonEmptyMap}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfigTest extends AnyFlatSpec with Matchers {
  it should "fail on duplicates" in {
    val dupe = StringFeatureSchema(
      FeatureName("foo"),
      FieldName(Item, "foo"),
      ItemScopeType,
      IndexEncoderName,
      NonEmptyList.of("x")
    )
    val conf = TestConfig().copy(features = NonEmptyList.of(dupe, dupe))
    Config.validateConfig(conf) shouldBe List("non-unique feature 'foo' is defined more than once")
  }

  it should "properly resolve feature names" in {
    val conf = TestConfig()
    Config.validateConfig(conf) shouldBe Nil
    Config.validateConfig(
      conf.copy(models =
        NonEmptyMap.of(
          "test" -> LambdaMARTConfig(
            XGBoostBackend(),
            NonEmptyList.of(FeatureName("price")),
            NonEmptyMap.of("click" -> 1)
          )
        )
      )
    ) shouldBe Nil
    Config.validateConfig(
      conf.copy(models =
        NonEmptyMap.of(
          "test" -> LambdaMARTConfig(
            XGBoostBackend(),
            NonEmptyList.of(FeatureName("neprice")),
            NonEmptyMap.of("click" -> 1)
          )
        )
      )
    ) shouldBe List("unresolved feature 'neprice' in model 'test'")
  }
}
