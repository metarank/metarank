package ai.metarank.feature

import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.Field.StringField
import ai.metarank.model.FieldName.EventType.{Item, Ranking}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.Scalar.SDouble
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.ScopeType.{ItemScopeType, RankingScopeType}
import ai.metarank.model.Write.Put
import ai.metarank.model.{FeatureSchema, FieldName, Key, Schema}
import ai.metarank.util.{TestItemEvent, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class WordCountFeatureRankingTest extends AnyFlatSpec with Matchers with FeatureTest {
  val feature = WordCountFeature(
    WordCountSchema(
      name = FeatureName("title_words"),
      scope = RankingScopeType,
      source = FieldName(Ranking, "query")
    )
  )
  val store = MemPersistence(Schema(feature.states))

  it should "decode schema" in {
    val conf    = "name: query_words\ntype: word_count\nscope: ranking\nsource: ranking.query"
    val decoded = parse(conf).flatMap(_.as[FeatureSchema])
    decoded shouldBe Right(WordCountSchema(FeatureName("query_words"), FieldName(Ranking, "query"), RankingScopeType))
  }

  it should "compute value" in {
    val values = process(
      Nil,
      feature.schema,
      TestRankingEvent(List("p1")).copy(fields = List(StringField("query", "hello world")))
    )
    values shouldBe List(List(SingleValue(FeatureName("title_words"), 2.0)))
  }

}
