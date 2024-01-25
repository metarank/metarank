package ai.metarank.feature

import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.Field.StringField
import ai.metarank.model.Identifier.{ItemId, RankingId}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.Scalar.SString
import ai.metarank.model.{EventId, FeatureSchema, Key, Schema}
import ai.metarank.model.Scope.{ItemScope, RankingScope}
import ai.metarank.model.ScopeType.{ItemFieldScopeType, RankingFieldScopeType}
import ai.metarank.model.Write.Put
import ai.metarank.util.{TestInteractionEvent, TestItemEvent, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class RankFieldScopedRateFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {
  val conf =
    RateFeatureSchema(
      FeatureName("ctr"),
      "click",
      "impression",
      RankingFieldScopeType("query"),
      24.hours,
      List(7, 14),
      refresh = Some(0.seconds)
    )
  val feature = RateFeature(conf)
  val store   = MemPersistence(Schema(feature.states))

  it should "decode schema" in {
    val in =
      "name: ctr\ntype: rate\ntop: click\nbottom: impression\nbucket: 24h\nperiods: [7,14]\nscope: ranking.query\nrefresh: 0s"
    parse(in).flatMap(_.as[FeatureSchema]) shouldBe Right(conf)
  }

  it should "extract writes when field matches" in {
    val rank = TestRankingEvent(List("p1", "p2")).copy(fields = List(StringField("query", "test")), id = EventId("r1"))
    val w1   = feature.writes(rank, store).unsafeRunSync()
    w1 shouldBe List(
      Put(Key(RankingScope(RankingId("r1")), feature.rankingFieldFeature.name), rank.timestamp, SString("test"))
    )
  }

  it should "compute value for field matches" in {
    val values = process(
      events = List(
        TestRankingEvent(List("p1", "p2")).copy(fields = List(StringField("query", "test")), id = EventId("r1")),
        TestInteractionEvent("p1", "r1").copy(`type` = "impression"),
        TestInteractionEvent("p2", "r1").copy(`type` = "impression"),
        TestInteractionEvent("p1", "r1").copy(`type` = "click"),
        TestRankingEvent(List("p1", "p2")).copy(fields = List(StringField("query", "test")), id = EventId("r2")),
        TestInteractionEvent("p1", "r2").copy(`type` = "impression"),
        TestInteractionEvent("p2", "r2").copy(`type` = "impression"),
        TestInteractionEvent("p2", "r2").copy(`type` = "click")
      ),
      feature.schema,
      TestRankingEvent(List("p1")).copy(fields = List(StringField("query", "test")))
    )
    values shouldBe List(List(VectorValue(FeatureName("ctr"), Array(0.5, 0.5), 2)))
  }

}
