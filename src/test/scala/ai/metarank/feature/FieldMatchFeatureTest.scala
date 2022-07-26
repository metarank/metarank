package ai.metarank.feature

import ai.metarank.feature.FieldMatchFeature.FieldMatchSchema
import ai.metarank.feature.matcher.NgramMatcher
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.Field.StringField
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.{Item, Ranking}
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.persistence.field.MapFieldStore
import ai.metarank.util.{TestItemEvent, TestRankingEvent, TextAnalyzer}
import io.findify.featury.model.{Key, SString, SStringList, ScalarValue, Timestamp}
import io.findify.featury.model.Key.{FeatureName, Tag, Tenant}
import io.findify.featury.model.Write.Put
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FieldMatchFeatureTest extends AnyFlatSpec with Matchers {
  val feature = FieldMatchFeature(
    FieldMatchSchema(
      name = "title_match",
      rankingField = FieldName(Ranking, "query"),
      itemField = FieldName(Item, "title"),
      method = NgramMatcher(3, TextAnalyzer.english)
    )
  )
  val now = Timestamp.now

  it should "generate puts" in {
    val event = TestItemEvent("p1", List(StringField("title", "foobar"))).copy(timestamp = now)
    val puts  = feature.writes(event, MapFieldStore()).toList
    puts shouldBe List(
      Put(
        Key(Tag(ItemScope.scope, "p1"), FeatureName("title_match"), Tenant("default")),
        now,
        SStringList(List("bar", "foo", "oba", "oob"))
      )
    )
  }

  it should "compute match score" in {
    val key     = Key(Tag(ItemScope.scope, "p1"), FeatureName("title_match"), Tenant("default"))
    val request = TestRankingEvent(List("p1")).copy(fields = List(StringField("query", "foo")))
    val result =
      feature.values(request, Map(key -> ScalarValue(key, now, SStringList(List("bar", "foo", "oba", "oob")))))
    result shouldBe List(SingleValue("title_match", 0.25))
  }
}
