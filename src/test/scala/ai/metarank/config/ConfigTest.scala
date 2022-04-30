package ai.metarank.config

import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.ModelConfig.ModelBackend.XGBoostBackend
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.util.TestConfig
import better.files.File
import cats.data.{NonEmptyList, NonEmptyMap}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfigTest extends AnyFlatSpec with Matchers {
  it should "fail on duplicates" in {
    val dupe = StringFeatureSchema("foo", FieldName(Item, "foo"), ItemScope, NonEmptyList.of("x"))
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
            MPath(File.newTemporaryFile()),
            XGBoostBackend(),
            NonEmptyList.of("price"),
            NonEmptyMap.of("click" -> 1)
          )
        )
      )
    ) shouldBe Nil
    Config.validateConfig(
      conf.copy(models =
        NonEmptyMap.of(
          "test" -> LambdaMARTConfig(
            MPath(File.newTemporaryFile()),
            XGBoostBackend(),
            NonEmptyList.of("neprice"),
            NonEmptyMap.of("click" -> 1)
          )
        )
      )
    ) shouldBe List("unresolved feature 'neprice' in model 'test'")
  }
}
