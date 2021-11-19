package ai.metarank.feature

import ai.metarank.model.FeatureSchema
import ai.metarank.model.FeatureSchema.{NumberFeatureSchema, WordCountSchema}
import ai.metarank.model.FeatureSource.Item
import ai.metarank.model.Field.StringField
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.{TestImpressionEvent, TestMetadataEvent}
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
      field = "title",
      source = Item
    )
  )

  it should "decode schema" in {
    val conf    = "name: title_words\ntype: word_count\nfield: title\nsource: item"
    val decoded = parse(conf).flatMap(_.as[FeatureSchema])
    decoded shouldBe Right(WordCountSchema("title_words", "title", Item))
  }

  it should "extract field" in {
    val event  = TestMetadataEvent("p1", List(StringField("title", "foo, bar, baz!")))
    val result = feature.writes(event)
    result shouldBe List(Put(Key(feature.states.head, Tenant("default"), "p1"), event.timestamp, SDouble(3)))
  }

  it should "compute value" in {
    val key = Key(feature.states.head, Tenant("default"), "p1")
    val result = feature.value(
      request = TestImpressionEvent(List("p1")),
      state = Map(key -> ScalarValue(key, Timestamp.now, SDouble(3))),
      id = "p1"
    )
    result shouldBe SingleValue("title_words", 3.0)

  }

}
