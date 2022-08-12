package ai.metarank.feature

import ai.metarank.feature.RefererFeature.RefererSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.ScopeType._
import ai.metarank.model.Field.StringField
import ai.metarank.model.{FieldName, Key, Timestamp}
import ai.metarank.model.FieldName.EventType.{Ranking, User}
import ai.metarank.model.Identifier.UserId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.{CategoryValue, VectorValue}
import ai.metarank.model.Scalar.SString
import ai.metarank.model.Scope.UserScope
import ai.metarank.model.Write.{Put, PutTuple}
import ai.metarank.util.{TestRankingEvent, TestUserEvent}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RefererFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {
  val feature = RefererFeature(
    RefererSchema(
      name = FeatureName("ref_medium"),
      source = FieldName(Ranking, "ref"),
      scope = UserScopeType
    )
  )
  val event =
    TestRankingEvent(List("p1")).copy(user = UserId("u1"), fields = List(StringField("ref", "http://www.google.com")))

  it should "extract referer field" in {
    val write = feature.writes(event, Persistence.blackhole()).unsafeRunSync().toList
    write shouldBe List(
      Put(Key(UserScope(UserId("u1")), FeatureName("ref_medium")), event.timestamp, SString("search"))
    )
  }

  it should "parse referer field from state" in {
    val values = process(List(event), feature.schema, TestRankingEvent(List("p1")).copy(user = UserId("u1")))
    values shouldBe List(List(CategoryValue(FeatureName("ref_medium"), 1)))
  }
}
