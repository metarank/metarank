package ai.metarank.feature

import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.feature.UserAgentFeature.UserAgentSchema
import ai.metarank.feature.ua.PlatformField
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.ScopeType.{ItemScope, SessionScope}
import ai.metarank.model.Field.StringField
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.{Item, Ranking}
import ai.metarank.model.Identifier.SessionId
import ai.metarank.model.MValue.VectorValue
import ai.metarank.util.TestRankingEvent
import cats.data.NonEmptyList
import io.findify.featury.model.{Key, SString, ScalarValue, Timestamp}
import io.findify.featury.model.Key.{FeatureName, Tag, Tenant}
import io.findify.featury.model.Write.Put
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UserAgentFeatureTest extends AnyFlatSpec with Matchers {
  lazy val now = Timestamp.now
  lazy val feature = UserAgentFeature(
    UserAgentSchema(
      name = "ua_platform",
      source = FieldName(Ranking, "ua"),
      field = PlatformField
    )
  )

  it should "compute value from ranking event" in {
    val value = feature.value(
      request = TestRankingEvent(List("p1")).copy(
        fields = List(StringField("ua", "Mozilla/4.0 (compatible; MSIE 9.0; Windows NT 6.1)")),
        session = Some(SessionId("s1")),
        timestamp = now
      ),
      features = Map.empty
    )
    value should matchPattern {
      case VectorValue(List("ua_platform_mobile", "ua_platform_desktop", "ua_platform_tablet"), values, 3)
          if values.toList == List(0.0, 1.0, 0.0) =>
    }
  }

  it should "compute value from past state" in {
    val k = Key(Tag(SessionScope.scope, "s1"), FeatureName("ua_platform"), Tenant("default"))
    val value = feature.value(
      request = TestRankingEvent(List("p1")).copy(
        session = Some(SessionId("s1")),
        timestamp = now,
        fields = List(StringField("ua", "Mozilla/4.0 (compatible; MSIE 9.0; Windows NT 6.1)"))
      ),
      features = Map.empty
    )
    value should matchPattern {
      case VectorValue(List("ua_platform_mobile", "ua_platform_desktop", "ua_platform_tablet"), values, 3)
          if values.toList == List(0.0, 1.0, 0.0) =>
    }
  }
}
