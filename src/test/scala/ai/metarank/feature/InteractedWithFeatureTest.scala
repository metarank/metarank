package ai.metarank.feature

import ai.metarank.FeatureMapping
import ai.metarank.config.ModelConfig.ShuffleConfig
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.flow.FeatureValueFlow
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.{Env, FeatureKey, FieldName, Key}
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Identifier.{ItemId, SessionId}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.Scalar.SString
import ai.metarank.model.Scope.{ItemScope, SessionScope}
import ai.metarank.model.ScopeType.{ItemScopeType, SessionScopeType}
import ai.metarank.model.Write.{Append, Put}
import ai.metarank.util.{TestInteractionEvent, TestItemEvent, TestRankingEvent, TestSchema}
import cats.data.{NonEmptyList, NonEmptyMap}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.parse

import scala.concurrent.duration._

class InteractedWithFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {
  val conf = InteractedWithSchema(
    name = FeatureName("seen_color"),
    interaction = "impression",
    field = FieldName(Item, "color"),
    scope = SessionScopeType,
    count = Some(10),
    duration = Some(24.hours)
  )
  val feature = InteractedWithFeature(conf)

  val itemEvent1 = TestItemEvent("p1", List(StringField("color", "red")))
  val itemEvent2 = TestItemEvent("p2", List(StringField("color", "green")))
  val interactionEvent1 =
    TestInteractionEvent("p1", "i1", Nil).copy(session = Some(SessionId("s1")), `type` = "impression")
  val interactionEvent2 =
    TestInteractionEvent("p2", "i1", Nil).copy(session = Some(SessionId("s1")), `type` = "impression")

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
    val writes = feature.writes(itemEvent1, Persistence.blackhole()).unsafeRunSync().toList
    writes shouldBe List(
      Put(
        Key(ItemScope(Env("default"), ItemId("p1")), FeatureName("seen_color_field")),
        itemEvent1.timestamp,
        SString("red")
      )
    )
  }

  it should "emit writes on meta list field" in {
    val event  = TestItemEvent("p1", List(StringListField("color", List("red"))))
    val writes = feature.writes(event, Persistence.blackhole()).unsafeRunSync().toList
    writes shouldBe List(
      Put(
        Key(ItemScope(Env("default"), ItemId("p1")), FeatureName("seen_color_field")),
        event.timestamp,
        SString("red")
      )
    )
  }

  it should "emit last colors on interaction" in {
    val state = MemPersistence(TestSchema(feature.schema))
    feature.writes(itemEvent1, state).unsafeRunSync()
    val appends = feature.writes(interactionEvent1, state).unsafeRunSync().toList
    appends shouldBe List(
      Append(
        key = Key(SessionScope(Env("default"), SessionId("s1")), FeatureName("seen_color_last")),
        ts = interactionEvent1.timestamp,
        value = SString("red")
      )
    )
  }

  it should "compute values" in {
    val values = process(
      List(itemEvent1, itemEvent2, interactionEvent1, interactionEvent2),
      feature.schema,
      TestRankingEvent(List("p1", "p2", "p3")).copy(session = Some(SessionId("s1")))
    )

    values shouldBe List()
  }
}
