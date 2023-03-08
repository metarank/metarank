package ai.metarank.validate

import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.model.Field.StringField
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.util.{TestConfig, TestItemEvent}
import ai.metarank.validate.checks.StringValuesValidation.{ItemStringValuesValidation, StringValuesValidationError}
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StringValuesValidationTest extends AnyFlatSpec with Matchers {
  it should "fail on unref field value" in {
    val items = List(
      TestItemEvent("p1", List(StringField("foo", "bar")))
    )
    val conf = TestConfig().copy(features =
      List(
        StringFeatureSchema(
          name = FeatureName("fpp"),
          field = FieldName(Item, "foo"),
          scope = ItemScopeType,
          values = NonEmptyList.of("bar", "baz", "quz", "zpp")
        )
      )
    )
    val result = ItemStringValuesValidation.validate(conf, items)
    result shouldBe List(StringValuesValidationError("foo", 25.0))
  }
}
