package ai.metarank.feature

import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.{Env, FeatureSchema, FieldName, Key}
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Field.StringField
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.Scalar.SDouble
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.Write.Put
import ai.metarank.util.{TestItemEvent, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class WordCountFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {
  val feature = WordCountFeature(
    WordCountSchema(
      name = FeatureName("title_words"),
      scope = ItemScopeType,
      source = FieldName(Item, "title")
    )
  )

  it should "decode schema" in {
    val conf    = "name: title_words\ntype: word_count\nscope: item\nsource: metadata.title"
    val decoded = parse(conf).flatMap(_.as[FeatureSchema])
    decoded shouldBe Right(WordCountSchema(FeatureName("title_words"), FieldName(Item, "title"), ItemScopeType))
  }

  it should "extract field" in {
    val event  = TestItemEvent("p1", List(StringField("title", "foo, bar, baz!")))
    val result = feature.writes(event, Persistence.blackhole()).unsafeRunSync().toList
    result shouldBe List(
      Put(Key(ItemScope(Env("default"), ItemId("p1")), FeatureName("title_words")), event.timestamp, SDouble(3))
    )
  }

  it should "compute value" in {
    val values = process(
      List(TestItemEvent("p1", List(StringField("title", "foo, bar, baz!")))),
      feature.schema,
      TestRankingEvent(List("p1"))
    )
    values shouldBe List(List(SingleValue("title_words", 3.0)))
  }

}
