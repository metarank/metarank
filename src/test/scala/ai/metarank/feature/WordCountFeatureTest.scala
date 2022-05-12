package ai.metarank.feature

import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.flow.FieldStore
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.{FeatureSchema, FieldName}
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Field.StringField
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.{TestItemEvent, TestRankingEvent}
import io.circe.yaml.parser.parse
import io.findify.featury.model.{Key, SDouble, SString, ScalarValue, Timestamp}
import io.findify.featury.model.Key.Tenant
import io.findify.featury.model.Write.Put
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class WordCountFeatureTest extends AnyFlatSpec with Matchers {
  val feature = WordCountFeature(
    WordCountSchema(
      name = "title_words",
      scope = ItemScope,
      source = FieldName(Item, "title")
    )
  )

  it should "decode schema" in {
    val conf    = "name: title_words\ntype: word_count\nscope: item\nsource: metadata.title"
    val decoded = parse(conf).flatMap(_.as[FeatureSchema])
    decoded shouldBe Right(WordCountSchema("title_words", FieldName(Item, "title"), ItemScope))
  }

  it should "extract field" in {
    val event  = TestItemEvent("p1", List(StringField("title", "foo, bar, baz!")))
    val result = feature.writes(event, FieldStore.empty).toList
    result shouldBe List(Put(Key(feature.states.head, Tenant("default"), "p1"), event.timestamp, SDouble(3)))
  }

  it should "compute value" in {
    val key = Key(feature.states.head, Tenant("default"), "p1")
    val result = feature.value(
      request = TestRankingEvent(List("p1")),
      features = Map(key -> ScalarValue(key, Timestamp.now, SDouble(3))),
      id = ItemRelevancy(ItemId("p1"))
    )
    result shouldBe SingleValue("title_words", 3.0)
  }

}
