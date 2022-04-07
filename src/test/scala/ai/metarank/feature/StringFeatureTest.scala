package ai.metarank.feature

import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.flow.FieldStore
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope}
import ai.metarank.model.FieldName.{Interaction, Item}
import ai.metarank.model.Field.StringField
import ai.metarank.model.Identifier.{ItemId, SessionId}
import ai.metarank.model.{FieldName, MValue}
import ai.metarank.model.MValue.VectorValue
import ai.metarank.util.{TestInteractionEvent, TestMetadataEvent, TestRankingEvent}
import cats.data.NonEmptyList
import io.findify.featury.model.{Key, SString, SStringList, ScalarValue, Timestamp}
import io.findify.featury.model.Key.{FeatureName, Tenant}
import io.findify.featury.model.Write.Put
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StringFeatureTest extends AnyFlatSpec with Matchers {
  val feature = StringFeature(
    StringFeatureSchema(
      name = "color",
      source = FieldName(Item, "color"),
      scope = ItemScope,
      values = NonEmptyList.of("red", "green", "blue")
    )
  )

  it should "extract color field" in {
    val event  = TestMetadataEvent("p1", List(StringField("color", "green")))
    val result = feature.writes(event, FieldStore.empty, FieldStore.empty)
    result shouldBe List(
      Put(Key(feature.states.head, Tenant("default"), "p1"), event.timestamp, SStringList(List("green")))
    )
  }

  it should "compute value" in {
    val key = Key(feature.states.head, Tenant("default"), "p1")
    val result = feature.value(
      request = TestRankingEvent(List("p1")),
      features = Map(key -> ScalarValue(key, Timestamp.now, SStringList(List("green")))),
      id = ItemRelevancy(ItemId("p1"))
    )
    result should matchPattern {
      case VectorValue(List("color_red", "color_green", "color_blue"), values, 3) if values.toList == List(0, 1, 0) =>
    }
  }

  it should "scope value to user" in {
    val feature = StringFeature(
      StringFeatureSchema(
        name = "country",
        source = FieldName(Interaction("click"), "country"),
        scope = SessionScope,
        values = NonEmptyList.of("a", "b", "c")
      )
    )
    val event =
      TestInteractionEvent("p1", "p0").copy(session = SessionId("s1"), fields = List(StringField("country", "b")))
    val write = feature.writes(event, FieldStore.empty, FieldStore.empty)
    val key   = Key(feature.states.head, Tenant("default"), "s1")
    write shouldBe List(Put(key, event.timestamp, SStringList(List("b"))))
    val value = feature.value(
      request = TestRankingEvent(List("p1")).copy(session = SessionId("s1")),
      features = Map(key -> ScalarValue(key, Timestamp.now, SStringList(List("b")))),
      id = ItemRelevancy(ItemId("p1"))
    )
    value should matchPattern {
      case VectorValue(List("country_a", "country_b", "country_c"), values, 3) if values.toList == List(0, 1, 0) =>
    }
  }
}
