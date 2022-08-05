package ai.metarank.feature

import ai.metarank.feature.FieldMatchFeature.FieldMatchSchema
import ai.metarank.feature.matcher.NgramMatcher
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.Field.StringField
import ai.metarank.model.{Env, FieldName, Key, Schema, Timestamp}
import ai.metarank.model.FieldName.EventType.{Item, Ranking}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.Scalar.SStringList
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.Write.Put
import ai.metarank.util.{TestItemEvent, TestRankingEvent, TextAnalyzer}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FieldMatchFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {
  val feature = FieldMatchFeature(
    FieldMatchSchema(
      name = FeatureName("title_match"),
      rankingField = FieldName(Ranking, "query"),
      itemField = FieldName(Item, "title"),
      method = NgramMatcher(3, TextAnalyzer.english)
    )
  )
  val now   = Timestamp.now
  val store = MemPersistence(Schema(feature.states))
  val event = TestItemEvent("p1", List(StringField("title", "foobar"))).copy(timestamp = now)

  it should "generate puts" in {
    val puts = feature.writes(event, store).unsafeRunSync()
    puts shouldBe List(
      Put(
        Key(ItemScope(Env("default"), ItemId("p1")), FeatureName("title_match_title")),
        now,
        SStringList(List("bar", "foo", "oba", "oob"))
      )
    )
  }

  it should "compute match score" in {
    val result = process(
      List(event),
      feature.schema,
      TestRankingEvent(List("p1")).copy(fields = List(StringField("query", "foo")))
    )
    result shouldBe List(SingleValue("title_match", 0.25))
  }
}
