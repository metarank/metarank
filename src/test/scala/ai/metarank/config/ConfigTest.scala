package ai.metarank.config

import ai.metarank.config.Config.ModelConfig.{LambdaMART, XGBoostBackend}
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.util.TestConfig
import cats.data.NonEmptyList
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
      conf.copy(models = Map("test" -> LambdaMART(XGBoostBackend, NonEmptyList.of("price"))))
    ) shouldBe Nil
    Config.validateConfig(
      conf.copy(models = Map("test" -> LambdaMART(XGBoostBackend, NonEmptyList.of("neprice"))))
    ) shouldBe List("unresolved feature 'neprice' in model 'test'")
  }
}
