package ai.metarank.feature

import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.flow.FieldStore
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope}
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.FieldId.ItemFieldId
import ai.metarank.model.{FieldId, FieldName}
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Identifier.{ItemId, SessionId}
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.{TestInteractionEvent, TestItemEvent, TestRankingEvent}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.parse
import io.findify.featury.model.{BoundedListValue, Key, SString, SStringList, ScalarValue, TimeValue, Timestamp}
import io.findify.featury.model.Key.{FeatureName, Scope, Tag, Tenant}
import io.findify.featury.model.Write.{Append, Put}

import scala.concurrent.duration._

class InteractedWithFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {
  val conf = InteractedWithSchema(
    name = "seen_color",
    interaction = "impression",
    field = FieldName(Item, "color"),
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
    val writes1 =
      feature.writes(TestItemEvent("p1", List(StringField("color", "red"))), FieldStore.empty)

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
    val event   = TestItemEvent("p1", List(StringListField("color", List("red"))))
    val writes1 = feature.writes(event, FieldStore.empty)
    val result = writes1.collect {
      case Put(
            Key(Tag(ItemScope.scope, "p1"), FeatureName("seen_color_field"), Tenant("default")),
            _,
            SString(value)
          ) =>
        value
    }
    result shouldBe List("red")
  }

  it should "emit bounded list appends" in {
    val writes1 =
      feature.writes(
        TestInteractionEvent("p1", "i1", Nil).copy(session = SessionId("s1"), `type` = "impression"),
        FieldStore.map(
          Map(ItemFieldId(Tenant("default"), ItemId("p1"), "color") -> StringField("color", "red"))
        )
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
      features = state,
      ItemRelevancy(ItemId("p2"))
    )
    values2 shouldBe SingleValue("seen_color", 1)
    val values3 = feature.value(
      request = request,
      features = state,
      ItemRelevancy(ItemId("p3"))
    )
    values3 shouldBe SingleValue("seen_color", 0)
    val values4 = feature.value(
      request = request,
      features = state,
      ItemRelevancy(ItemId("404"))
    )
    values4 shouldBe SingleValue("seen_color", 0)
  }

  it should "process events" in {
    val conf = InteractedWithSchema(
      name = "seen_color",
      interaction = "click",
      field = FieldName(Item, "color"),
      scope = SessionScope,
      count = Some(10),
      duration = Some(24.hours)
    )
    val result = process(
      events = List(
        TestItemEvent("p1", List(StringListField("color", List("red")))),
        TestInteractionEvent("p1", "x")
      ),
      schema = conf,
      request = TestRankingEvent(List("p1"))
    )
    result should matchPattern { case List(List(SingleValue("seen_color", 1.0))) =>
    }
  }
}
