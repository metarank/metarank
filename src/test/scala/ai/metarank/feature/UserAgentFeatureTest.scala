package ai.metarank.feature

import ai.metarank.feature.UserAgentFeature.UserAgentSchema
import ai.metarank.feature.ua.PlatformField
import ai.metarank.model.Field.StringField
import ai.metarank.model.{FieldName, Timestamp}
import ai.metarank.model.FieldName.EventType.{Item, Ranking}
import ai.metarank.model.Identifier.SessionId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.VectorValue
import ai.metarank.util.TestRankingEvent
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UserAgentFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {
  lazy val now = Timestamp.now
  lazy val feature =
    UserAgentSchema(
      name = FeatureName("ua_platform"),
      source = FieldName(Ranking, "ua"),
      field = PlatformField
    ).create().unsafeRunSync().asInstanceOf[UserAgentFeature]

  val event = TestRankingEvent(List("p1")).copy(
    fields = List(StringField("ua", "Mozilla/4.0 (compatible; MSIE 9.0; Windows NT 6.1)")),
    session = Some(SessionId("s1")),
    timestamp = now
  )

  it should "compute value from previous events" in {
    val values = process(List(event), feature.schema, event)
    values shouldBe List(
      List(
        VectorValue(FeatureName("ua_platform"), Array(0.0, 1.0, 0.0), 3)
      )
    )
  }

  it should "compute value from first ranking" in {
    val value = feature.value(event, Map.empty)
    value shouldBe VectorValue(
      FeatureName("ua_platform"),
      Array(0.0, 1.0, 0.0),
      3
    )

  }
}
