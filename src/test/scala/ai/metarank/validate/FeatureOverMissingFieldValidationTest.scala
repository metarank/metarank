package ai.metarank.validate

import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.model.Event.RankItem
import ai.metarank.model.Field.{NumberField, StringField}
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.{Item, Ranking}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.util.{TestConfig, TestItemEvent, TestRankingEvent}
import ai.metarank.validate.checks.FeatureOverMissingFieldValidation
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FeatureOverMissingFieldValidationTest extends AnyFlatSpec with Matchers {
  val conf = TestConfig().copy(features =
    NonEmptyList.of(
      StringFeatureSchema(
        name = FeatureName("size"),
        source = FieldName(Item, "size"),
        scope = ItemScopeType,
        values = NonEmptyList.of("small", " big")
      )
    )
  )

  it should "check field refs" in {
    val result = FeatureOverMissingFieldValidation.validate(
      config = conf,
      events = List(TestItemEvent("p1", List(StringField("size", "small"))))
    )
    result shouldBe empty
  }

  it should "fail on missing field" in {
    val result = FeatureOverMissingFieldValidation.validate(
      config = conf,
      events = List(TestItemEvent("p1", List(StringField("color", "red"))))
    )
    result shouldNot be(empty)
  }

  it should "handle relevancy fields from ranking" in {
    val result = FeatureOverMissingFieldValidation.validate(
      config = TestConfig().copy(features =
        NonEmptyList.of(
          NumberFeatureSchema(
            name = FeatureName("size"),
            source = FieldName(Item, "relevancy"),
            scope = ItemScopeType
          )
        )
      ),
      events = List(
        TestRankingEvent(List("p1"))
          .copy(items = NonEmptyList.one(RankItem(ItemId("p1"), List(NumberField("relevancy", 1.0)))))
      )
    )
    result shouldBe empty
  }

  it should "fail on ranking.relevancy" in {
    val result = FeatureOverMissingFieldValidation.validate(
      config = TestConfig().copy(features =
        NonEmptyList.of(
          NumberFeatureSchema(
            name = FeatureName("size"),
            source = FieldName(Ranking, "relevancy"),
            scope = ItemScopeType
          )
        )
      ),
      events = List(
        TestRankingEvent(List("p1"))
          .copy(items = NonEmptyList.one(RankItem(ItemId("p1"), List(NumberField("relevancy", 1.0)))))
      )
    )
    result shouldNot be(empty)
  }
}
