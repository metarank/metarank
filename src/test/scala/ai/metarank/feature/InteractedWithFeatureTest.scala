package ai.metarank.feature

import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope}
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.{FieldName, ItemId, SessionId}
import ai.metarank.model.FieldName.Metadata
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.{TestInteractionEvent, TestMetadataEvent, TestRankingEvent}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.parse
import io.findify.featury.model.{BoundedListValue, Key, SString, SStringList, ScalarValue, TimeValue, Timestamp}
import io.findify.featury.model.Key.{FeatureName, Scope, Tag, Tenant}
import io.findify.featury.model.Write.{Append, Put}

import scala.concurrent.duration._

class InteractedWithFeatureTest extends AnyFlatSpec with Matchers {
  val conf = InteractedWithSchema(
    name = "seen_color",
    interaction = "impression",
    field = FieldName(Metadata, "color"),
    scope = SessionScope,
    count = Some(10),
    duration = Some(24.hours)
  )
  val feature = InteractedWithFeature(conf)

  it should "decode config" in {
    val yaml =
      """name: seen_color
        |interaction: impression
        |field: metadata.color
        |scope: session
        |count: 10
        |duration: 24h""".stripMargin
    parse(yaml).flatMap(_.as[InteractedWithSchema]) shouldBe Right(conf)
  }

  it should "emit writes on meta field" in {
    val writes1 = feature.writes(TestMetadataEvent("p1", List(StringField("color", "red"))))

    writes1.collect {
      case Put(
            Key(Tag(ItemScope.scope, "p1"), FeatureName("seen_color_field"), Tenant("default")),
            _,
            SString(value)
          ) =>
        value
    } shouldBe List("red")
  }

  it should "emit writes on meta list field" in {
    val writes1 = feature.writes(TestMetadataEvent("p1", List(StringListField("color", List("red")))))

    writes1.collect {
      case Put(
            Key(Tag(ItemScope.scope, "p1"), FeatureName("seen_color_field"), Tenant("default")),
            _,
            SStringList(value)
          ) =>
        value
    }.flatten shouldBe List("red")
  }

  it should "emit bounded list appends" in {
    val key = Key(Tag(ItemScope.scope, "p1"), FeatureName("seen_color_field"), Tenant("default"))
    val writes1 =
      feature.writes(
        TestInteractionEvent("p1", "i1", Nil).copy(session = SessionId("s1"), `type` = "impression"),
        Map(key -> ScalarValue(key, Timestamp.now, SString("red")))
      )
    val result = writes1.collect {
      case Append(
            Key(Tag(SessionScope.scope, "s1"), FeatureName("seen_color_last"), Tenant("default")),
            value,
            _
          ) =>
        value
    }
    result shouldBe List(SString("red"))
  }

  it should "compute values" in {
    val itemKey1 = Key(Tag(ItemScope.scope, "p1"), FeatureName("seen_color_field"), Tenant("default"))
    val itemKey2 = Key(Tag(ItemScope.scope, "p2"), FeatureName("seen_color_field"), Tenant("default"))
    val itemKey3 = Key(Tag(ItemScope.scope, "p3"), FeatureName("seen_color_field"), Tenant("default"))
    val sesKey =
      Key(Tag(SessionScope.scope, "s1"), FeatureName("seen_color_last"), Tenant("default"))
    val state = Map(
      itemKey1 -> ScalarValue(itemKey1, Timestamp.now, SString("red")),
      itemKey2 -> ScalarValue(itemKey2, Timestamp.now, SString("red")),
      itemKey3 -> ScalarValue(itemKey3, Timestamp.now, SString("green")),
      sesKey   -> BoundedListValue(sesKey, Timestamp.now, List(TimeValue(Timestamp.now, SString("red"))))
    )
    val request = TestRankingEvent(List("p1", "p2", "p3")).copy(session = SessionId("s1"))

    val values2 = feature.value(
      request = request,
      state = state,
      ItemId("p2")
    )
    values2 shouldBe SingleValue("seen_color", 1)
    val values3 = feature.value(
      request = request,
      state = state,
      ItemId("p3")
    )
    values3 shouldBe SingleValue("seen_color", 0)
    val values4 = feature.value(
      request = request,
      state = state,
      ItemId("404")
    )
    values4 shouldBe SingleValue("seen_color", 0)
  }
}
