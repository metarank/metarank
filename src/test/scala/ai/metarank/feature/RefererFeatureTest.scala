package ai.metarank.feature

import ai.metarank.feature.RefererFeature.RefererSchema
import ai.metarank.flow.FieldStore
import ai.metarank.model.FeatureScope.UserScope
import ai.metarank.model.Field.StringField
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.{Ranking, User}
import ai.metarank.model.MValue.VectorValue
import ai.metarank.util.{TestRankingEvent, TestUserEvent}
import io.findify.featury.model.{FeatureValue, Key, SString, ScalarValue, Timestamp}
import io.findify.featury.model.Key.Tenant
import io.findify.featury.model.Write.Put
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RefererFeatureTest extends AnyFlatSpec with Matchers {
  val feature = RefererFeature(
    RefererSchema(
      name = "ref_medium",
      source = FieldName(User, "ref"),
      scope = UserScope
    )
  )
  val now = Timestamp.now
  it should "extract referer field" in {
    val event = TestUserEvent("u1", List(StringField("ref", "http://www.google.com")))
    val write = feature.writes(event, FieldStore.empty)
    write shouldBe List(
      Put(Key(feature.conf, Tenant("default"), "u1"), event.timestamp, SString("http://www.google.com"))
    )
  }

  it should "parse referer field from state" in {
    val ranking  = TestRankingEvent(List("p1"))
    val k        = Key(feature.conf, Tenant("default"), "u1")
    val features = Map(k -> ScalarValue(k, now, SString("http://www.google.com")))
    val result   = feature.value(ranking, features)
    result should matchPattern {
      case VectorValue(_, values, _) if values.toList == List(0.0, 1.0, 0.0, 0.0, 0.0, 0.0) =>
    }
  }
}
