package ai.metarank.feature

import ai.metarank.feature.FieldMatchFeature.FieldMatchSchema
import ai.metarank.feature.matcher.{FieldMatcher, NgramMatcher, TermMatcher}
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.Field.StringField
import ai.metarank.model.{FieldName, Key, Schema, Timestamp}
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
import io.circe.yaml.parser.parse

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

  import FieldMatchFeature._

  it should "parse ngram config" in {
    val result = parse("type: ngram\nn: 3\nlanguage: en").flatMap(_.as[FieldMatcher])
    result shouldBe Right(NgramMatcher(3, TextAnalyzer.english))
  }

  it should "parse term config" in {
    val result = parse("type: term\nlanguage: en").flatMap(_.as[FieldMatcher])
    result shouldBe Right(TermMatcher(TextAnalyzer.english))
  }

  it should "generate puts" in {
    val puts = feature.writes(event).unsafeRunSync().toList
    puts shouldBe List(
      Put(
        Key(ItemScope(ItemId("p1")), FeatureName("title_match_title")),
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
    result shouldBe List(List(SingleValue(FeatureName("title_match"), 0.25)))
  }
}
