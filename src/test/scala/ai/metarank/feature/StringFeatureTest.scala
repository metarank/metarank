package ai.metarank.feature

import ai.metarank.feature.StringFeature.EncoderName.{IndexEncoderName, OnehotEncoderName}
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.FieldName.EventType.{Interaction, Item, User}
import ai.metarank.model.Field.StringField
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{FieldName, Key, MValue}
import ai.metarank.model.MValue.{CategoryValue, VectorValue}
import ai.metarank.model.Scalar.SStringList
import ai.metarank.model.Scope.{ItemScope, UserScope}
import ai.metarank.model.ScopeType.{ItemScopeType, SessionScopeType, UserScopeType}
import ai.metarank.model.Write.Put
import ai.metarank.util.{TestInteractionEvent, TestItemEvent, TestRankingEvent, TestUserEvent}
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StringFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {
  val feature = StringFeature(
    StringFeatureSchema(
      name = FeatureName("color"),
      source = FieldName(Item, "color"),
      scope = ItemScopeType,
      values = NonEmptyList.of("red", "green", "blue")
    )
  )

  it should "extract item field" in {
    val event  = TestItemEvent("p1", List(StringField("color", "green")))
    val result = feature.writes(event, Persistence.blackhole()).unsafeRunSync().toList
    result shouldBe List(
      Put(
        Key(ItemScope(ItemId("p1")), FeatureName("color")),
        event.timestamp,
        SStringList(List("green"))
      )
    )
  }

  it should "extract user field" in {
    val feature = StringFeature(
      StringFeatureSchema(
        name = FeatureName("user_gender"),
        source = FieldName(User, "gender"),
        scope = UserScopeType,
        values = NonEmptyList.of("female", "male")
      )
    )
    val event  = TestUserEvent("u1", List(StringField("gender", "male")))
    val result = feature.writes(event, Persistence.blackhole()).unsafeRunSync().toList
    result shouldBe List(
      Put(
        Key(UserScope(UserId("u1")), FeatureName("user_gender")),
        event.timestamp,
        SStringList(List("male"))
      )
    )
  }

  it should "compute value for item" in {
    val values = process(
      List(TestItemEvent("p1", List(StringField("color", "green")))),
      feature.schema,
      TestRankingEvent(List("p1"))
    )
    values shouldBe List(List(CategoryValue(FeatureName("color"), "green", 2)))
  }

  it should "scope value to user" in {
    val feature = StringFeature(
      StringFeatureSchema(
        name = FeatureName("country"),
        source = FieldName(Interaction("click"), "country"),
        scope = SessionScopeType,
        values = NonEmptyList.of("US", "EU")
      )
    )
    val event =
      TestInteractionEvent("p1", "p0").copy(
        session = Some(SessionId("s1")),
        fields = List(StringField("country", "EU"))
      )
    val values =
      process(List(event), feature.schema, TestRankingEvent(List("p1")).copy(session = Some(SessionId("s1"))))
    values shouldBe List(List(CategoryValue(FeatureName("country"), "EU", 2)))
  }

  it should "onehot encode values" in {
    val feature = StringFeature(
      StringFeatureSchema(
        name = FeatureName("country"),
        source = FieldName(Interaction("click"), "country"),
        scope = SessionScopeType,
        values = NonEmptyList.of("us", "eu"),
        encode = OnehotEncoderName
      )
    )
    val event =
      TestInteractionEvent("p1", "p0").copy(
        session = Some(SessionId("s1")),
        fields = List(StringField("country", "eu"))
      )
    val values =
      process(List(event), feature.schema, TestRankingEvent(List("p1")).copy(session = Some(SessionId("s1"))))
    values shouldBe List(List(VectorValue(FeatureName("country"), Array(0.0, 1.0), 2)))
  }

}
