package ai.metarank.feature

import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.flow.FieldStore
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.{FeatureSchema, FieldName}
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.FieldName.{Interaction, Item}
import ai.metarank.model.Field.{NumberField, StringField}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.MValue.{SingleValue, VectorValue}
import ai.metarank.util.{TestInteractionEvent, TestMetadataEvent, TestRankingEvent}
import io.circe.yaml.parser.parse
import io.findify.featury.model.Key.Tenant
import io.findify.featury.model.{Key, SDouble, SString, ScalarValue, Timestamp}
import io.findify.featury.model.Write.Put
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class NumberFeatureTest extends AnyFlatSpec with Matchers {
  val feature = NumberFeature(
    NumberFeatureSchema(
      name = "popularity",
      source = FieldName(Item, "popularity"),
      scope = ItemScope
    )
  )

  it should "decode schema" in {
    parse("name: price\ntype: number\nscope: item\nsource: metadata.price\nrefresh: 1m").flatMap(
      _.as[FeatureSchema]
    ) shouldBe Right(
      NumberFeatureSchema("price", FieldName(Item, "price"), ItemScope, Some(1.minute))
    )
  }

  it should "extract field from metadata" in {
    val event  = TestMetadataEvent("p1", List(NumberField("popularity", 100)))
    val result = feature.writes(event, FieldStore.empty, FieldStore.empty)
    result shouldBe List(
      Put(Key(feature.states.head, Tenant("default"), "p1"), event.timestamp, SDouble(100))
    )
  }

  it should "extract field from interaction" in {
    val feature = NumberFeature(
      NumberFeatureSchema(
        name = "popularity",
        source = FieldName(Interaction("click"), "popularity"),
        scope = ItemScope
      )
    )

    val event  = TestInteractionEvent("p1", "k1", List(NumberField("popularity", 100))).copy(`type` = "click")
    val result = feature.writes(event, FieldStore.empty, FieldStore.empty)
    result shouldBe List(
      Put(Key(feature.states.head, Tenant("default"), "p1"), event.timestamp, SDouble(100))
    )
  }

  it should "compute value" in {
    val key = Key(feature.states.head, Tenant("default"), "p1")
    val result = feature.value(
      request = TestRankingEvent(List("p1")),
      features = Map(key -> ScalarValue(key, Timestamp.now, SDouble(100))),
      id = ItemRelevancy(ItemId("p1"))
    )
    result shouldBe SingleValue("popularity", 100.0)
  }

}
