package ai.metarank.feature

import ai.metarank.model.Event.MetadataEvent
import ai.metarank.model.FeatureSchema.StringFeatureSchema
import ai.metarank.model.FeatureSource.Item
import ai.metarank.model.Field.StringField
import ai.metarank.model.MValue
import ai.metarank.model.MValue.VectorValue
import ai.metarank.util.{TestImpressionEvent, TestMetadataEvent}
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
      field = "color",
      source = Item,
      values = NonEmptyList.of("red", "green", "blue")
    )
  )

  it should "extract color field" in {
    val event  = TestMetadataEvent("p1", List(StringField("color", "green")))
    val result = feature.writes(event)
    result shouldBe List(
      Put(Key(feature.states.head, Tenant("default"), "p1"), event.timestamp, SStringList(List("green")))
    )
  }

  it should "compute value" in {
    val key = Key(feature.states.head, Tenant("default"), "p1")
    val result = feature.value(
      request = TestImpressionEvent(List("p1")),
      state = Map(key -> ScalarValue(key, Timestamp.now, SStringList(List("green")))),
      id = "p1"
    )
    result should matchPattern {
      case VectorValue(List("color_red", "color_green", "color_blue"), values, 3) if values.toList == List(0, 1, 0) =>
    }
  }
}
