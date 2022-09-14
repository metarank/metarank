package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.config.CoreConfig
import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.ModelConfig.ModelBackend.XGBoostBackend
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.StringFeature.EncoderName.IndexEncoderName
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Field.{NumberField, StringField, StringListField}
import ai.metarank.model.{
  Clickthrough,
  ClickthroughValues,
  EventId,
  FeatureKey,
  FieldName,
  ItemValue,
  Key,
  MValue,
  Timestamp
}
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.{CategoryValue, SingleValue}
import ai.metarank.model.Scalar.{SDouble, SString, SStringList}
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.ScopeType.{ItemScopeType, SessionScopeType}
import ai.metarank.rank.Ranker
import ai.metarank.util.{TestInteractionEvent, TestItemEvent, TestRankingEvent}
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import fs2.Stream

import java.util.UUID
import scala.concurrent.duration._

class MetarankFlowTest extends AnyFlatSpec with Matchers {
  val features = NonEmptyList.of(
    NumberFeatureSchema(FeatureName("pop"), FieldName(Item, "pop"), ItemScopeType),
    StringFeatureSchema(
      FeatureName("genre"),
      FieldName(Item, "genre"),
      ItemScopeType,
      Some(IndexEncoderName),
      NonEmptyList.of("action", "drama", "comedy")
    ),
    InteractedWithSchema(
      FeatureName("liked_genre"),
      "click",
      FieldName(Item, "genre"),
      SessionScopeType,
      count = Some(10),
      duration = Some(24.hours)
    )
  )
  val models = Map(
    "random" -> LambdaMARTConfig(
      backend = XGBoostBackend(),
      features = features.map(_.name),
      weights = Map("click" -> 1)
    )
  )
  val mapping     = FeatureMapping.fromFeatureSchema(features, models)
  val store       = MemPersistence(mapping.schema)
  val ts          = Timestamp.now
  val ranker      = Ranker(mapping, store)
  lazy val buffer = ClickthroughJoinBuffer(ClickthroughJoinConfig(), store, mapping)

  val rankingEvent1 = TestRankingEvent(List("p1", "p2", "p3"))
  val rankingEvent2 = rankingEvent1.copy(id = EventId(UUID.randomUUID().toString))
  val clickEvent1   = TestInteractionEvent("p2", rankingEvent1.id.value)
  val clickEvent2   = TestInteractionEvent("p1", rankingEvent2.id.value)

  it should "accept item events" in {
    val items = List(
      TestItemEvent("p1", List(NumberField("pop", 10), StringField("genre", "action"))).copy(timestamp = ts),
      TestItemEvent("p2", List(NumberField("pop", 5), StringListField("genre", List("comedy")))).copy(timestamp = ts),
      TestItemEvent("p3", List(NumberField("pop", 15), StringField("genre", "drama"))).copy(timestamp = ts)
    )
    MetarankFlow.process(store, Stream.emits(items), mapping, buffer).unsafeRunSync()
  }

  it should "have popularities values present in store" in {
    val k1           = Key(ItemScope(ItemId("p1")), FeatureName("pop"))
    val k2           = Key(ItemScope(ItemId("p2")), FeatureName("pop"))
    val k3           = Key(ItemScope(ItemId("p3")), FeatureName("pop"))
    val popularities = store.values.get(List(k1, k2, k3)).unsafeRunSync()
    popularities shouldBe Map(
      k1 -> ScalarValue(k1, ts, SDouble(10)),
      k2 -> ScalarValue(k2, ts, SDouble(5)),
      k3 -> ScalarValue(k3, ts, SDouble(15))
    )
  }

  it should "have genres present in store" in {
    val k1     = Key(ItemScope(ItemId("p1")), FeatureName("genre"))
    val k2     = Key(ItemScope(ItemId("p2")), FeatureName("genre"))
    val k3     = Key(ItemScope(ItemId("p3")), FeatureName("genre"))
    val genres = store.values.get(List(k1, k2, k3)).unsafeRunSync()
    genres shouldBe Map(
      k1 -> ScalarValue(k1, ts, SStringList("action")),
      k2 -> ScalarValue(k2, ts, SStringList("comedy")),
      k3 -> ScalarValue(k3, ts, SStringList("drama"))
    )
  }

  it should "generate query for a ranking request" in {
    val q = ranker.makeQuery(rankingEvent1, mapping.models("random").datasetDescriptor).unsafeRunSync()
    q.values shouldBe List(
      ItemValue(ItemId("p1"), List(MValue("pop", 10), MValue("genre", "action", 1), MValue("liked_genre", 0))),
      ItemValue(ItemId("p2"), List(MValue("pop", 5), MValue("genre", "comedy", 3), MValue("liked_genre", 0))),
      ItemValue(ItemId("p3"), List(MValue("pop", 15), MValue("genre", "drama", 2), MValue("liked_genre", 0)))
    )
    q.query.values.toList shouldBe List(
      10.0, 1.0, 0.0, // p1
      5.0, 3.0, 0.0,  // p2
      15.0, 2.0, 0.0  // p3
    )
  }

  it should "send click" in {
    MetarankFlow.process(store, Stream.emit(clickEvent1), mapping, buffer).unsafeRunSync()
  }

  it should "generate updated query" in {
    val q = ranker.makeQuery(rankingEvent2, mapping.models("random").datasetDescriptor).unsafeRunSync()
    q.values shouldBe List(
      ItemValue(ItemId("p1"), List(MValue("pop", 10), MValue("genre", "action", 1), MValue("liked_genre", 0))),
      ItemValue(ItemId("p2"), List(MValue("pop", 5), MValue("genre", "comedy", 3), MValue("liked_genre", 1))),
      ItemValue(ItemId("p3"), List(MValue("pop", 15), MValue("genre", "drama", 2), MValue("liked_genre", 0)))
    )
    q.query.values.toList shouldBe List(
      10.0, 1.0, 0.0, // p1
      5.0, 3.0, 1.0,  // p2
      15.0, 2.0, 0.0  // p3
    )
  }

  it should "create updated clickthrough in store" in {
    MetarankFlow.process(store, Stream.emits(List(rankingEvent2, clickEvent2)), mapping, buffer).unsafeRunSync()
    buffer.flushQueue(Timestamp.max).unsafeRunSync()
    val ctv = store.cts.getall().compile.toList.unsafeRunSync()
    ctv.find(_.ct.id == rankingEvent2.id) shouldBe Some(
      ClickthroughValues(
        ct = Clickthrough(
          rankingEvent2.id,
          rankingEvent2.timestamp,
          rankingEvent1.user,
          rankingEvent1.session,
          List(ItemId("p1"), ItemId("p2"), ItemId("p3")),
          interactions = List(TypedInteraction(ItemId("p1"), "click"))
        ),
        values = List(
          ItemValue(ItemId("p1"), List(MValue("pop", 10), MValue("genre", "action", 1), MValue("liked_genre", 0))),
          ItemValue(ItemId("p2"), List(MValue("pop", 5), MValue("genre", "comedy", 3), MValue("liked_genre", 1))),
          ItemValue(ItemId("p3"), List(MValue("pop", 15), MValue("genre", "drama", 2), MValue("liked_genre", 0)))
        )
      )
    )

  }

}
