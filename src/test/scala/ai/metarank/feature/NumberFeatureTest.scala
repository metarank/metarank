package ai.metarank.feature

import ai.metarank.model.FeatureSchema
import ai.metarank.model.FeatureSchema.{NumberFeatureSchema, StringFeatureSchema}
import ai.metarank.model.FeatureSource.Item
import ai.metarank.model.Field.{NumberField, StringField}
import ai.metarank.model.MValue.{SingleValue, VectorValue}
import ai.metarank.util.{TestImpressionEvent, TestMetadataEvent}
import cats.data.NonEmptyList
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
      field = "popularity",
      source = Item
    )
  )

  it should "decode schema" in {
    parse("name: price\ntype: number\nfield: price\nsource: item\nrefresh: 1m").flatMap(
      _.as[FeatureSchema]
    ) shouldBe Right(
      NumberFeatureSchema("price", "price", Item, Some(1.minute))
    )
  }

  it should "extract color field" in {
    val event  = TestMetadataEvent("p1", List(NumberField("popularity", 100)))
    val result = feature.writes(event)
    result shouldBe List(
      Put(Key(feature.states.head, Tenant("default"), "p1"), event.timestamp, SDouble(100))
    )
  }

  it should "compute value" in {
    val key = Key(feature.states.head, Tenant("default"), "p1")
    val result = feature.value(
      request = TestImpressionEvent(List("p1")),
      state = Map(key -> ScalarValue(key, Timestamp.now, SDouble(100))),
      id = "p1"
    )
    result shouldBe SingleValue("popularity", 100.0)
  }

}
