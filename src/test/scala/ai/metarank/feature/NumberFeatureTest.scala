package ai.metarank.feature

import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.Event.RankItem
import ai.metarank.model.{FeatureSchema, FieldName, Key, Schema}
import ai.metarank.model.ScopeType.{ItemScopeType, RankingScopeType, UserScopeType}
import ai.metarank.model.FieldName.EventType.{Interaction, Item, Ranking, User}
import ai.metarank.model.Field.{NumberField, StringField}
import ai.metarank.model.Identifier.{ItemId, UserId}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.{SingleValue, VectorValue}
import ai.metarank.model.Scalar.SDouble
import ai.metarank.model.Scope.{ItemScope, UserScope}
import ai.metarank.model.Write.Put
import ai.metarank.util.{TestInteractionEvent, TestItemEvent, TestRankingEvent, TestSchema, TestUserEvent}
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class NumberFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {
  val feature = NumberFeature(
    NumberFeatureSchema(
      name = FeatureName("popularity"),
      field = FieldName(Item, "popularity"),
      scope = ItemScopeType
    )
  )
  val store = MemPersistence(Schema(feature.states))

  it should "decode schema" in {
    parse("name: price\ntype: number\nscope: item\nsource: metadata.price\nrefresh: 1m").flatMap(
      _.as[FeatureSchema]
    ) shouldBe Right(
      NumberFeatureSchema(FeatureName("price"), FieldName(Item, "price"), ItemScopeType, Some(1.minute))
    )
  }

  it should "decode schema with field instead of source" in {
    parse("name: price\ntype: number\nscope: item\nfield: metadata.price\nrefresh: 1m").flatMap(
      _.as[FeatureSchema]
    ) shouldBe Right(
      NumberFeatureSchema(FeatureName("price"), FieldName(Item, "price"), ItemScopeType, Some(1.minute))
    )
  }

  it should "extract field from metadata" in {
    val event  = TestItemEvent("p1", List(NumberField("popularity", 100)))
    val result = feature.writes(event, store).unsafeRunSync().toList
    result shouldBe List(
      Put(Key(ItemScope(ItemId("p1")), FeatureName("popularity")), event.timestamp, SDouble(100))
    )
  }

  it should "extract field from interaction" in {
    val feature = NumberFeature(
      NumberFeatureSchema(
        name = FeatureName("popularity"),
        field = FieldName(Interaction("click"), "popularity"),
        scope = ItemScopeType
      )
    )

    val event  = TestInteractionEvent("p1", "k1", List(NumberField("popularity", 100))).copy(`type` = "click")
    val result = feature.writes(event, store).unsafeRunSync().toList
    result shouldBe List(
      Put(Key(ItemScope(ItemId("p1")), FeatureName("popularity")), event.timestamp, SDouble(100))
    )
  }

  it should "extract field from user profile" in {
    val feature = NumberFeature(
      NumberFeatureSchema(
        name = FeatureName("user_age"),
        field = FieldName(User, "age"),
        scope = UserScopeType
      )
    )

    val event  = TestUserEvent("u1", List(NumberField("age", 33)))
    val result = feature.writes(event, store).unsafeRunSync().toList
    result shouldBe List(
      Put(Key(UserScope(UserId("u1")), FeatureName("user_age")), event.timestamp, SDouble(33))
    )
  }

  it should "compute value" in {
    val values = process(
      List(TestItemEvent("p1", List(NumberField("popularity", 100)))),
      feature.schema,
      TestRankingEvent(List("p1"))
    )
    values shouldBe List(List(SingleValue(FeatureName("popularity"), 100.0)))
  }

  it should "compute value with field override" in {
    val values = process(
      Nil,
      feature.schema,
      TestRankingEvent(List("p1"))
        .copy(items = NonEmptyList.one(RankItem(ItemId("p1"), List(NumberField("popularity", 100)))))
    )
    values shouldBe List(List(SingleValue(FeatureName("popularity"), 100.0)))
  }

  it should "compute value with ranking events" in {
    val feature = NumberFeature(
      NumberFeatureSchema(
        name = FeatureName("weather_temp"),
        field = FieldName(Ranking, "temp"),
        scope = RankingScopeType
      )
    )
    val values = process(
      Nil,
      feature.schema,
      TestRankingEvent(List("p1")).copy(fields = List(NumberField("temp", 10)))
    )
    values shouldBe List(List(SingleValue(FeatureName("weather_temp"), 10.0)))

  }

}
