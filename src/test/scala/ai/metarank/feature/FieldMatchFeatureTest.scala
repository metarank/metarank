package ai.metarank.feature

import ai.metarank.feature.FieldMatchFeature.{FieldMatchSchema, NgramMethod}
import ai.metarank.flow.FieldStore
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.Field.StringField
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.{Item, Ranking}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.{TestItemEvent, TestRankingEvent}
import io.findify.featury.model.{Key, SString, ScalarValue, Timestamp}
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
      method = NgramMethod(3)
    )
  )
  val now = Timestamp.now
  it should "generate puts" in {
    val event = TestItemEvent("p1", List(StringField("title", "foobar"))).copy(timestamp = now)
    val puts  = feature.writes(event, FieldStore.empty).toList
    puts shouldBe List(
      Put(Key(Tag(ItemScope.scope, "p1"), FeatureName("title_match"), Tenant("default")), now, SString("foobar"))
    )
  }

  it should "compute match score" in {
    val key     = Key(Tag(ItemScope.scope, "p1"), FeatureName("title_match"), Tenant("default"))
    val request = TestRankingEvent(List("p1")).copy(fields = List(StringField("query", "foo")))
    val result =
      feature.value(request, Map(key -> ScalarValue(key, now, SString("foobar"))), ItemRelevancy(ItemId("p1")))
    result shouldBe SingleValue("title_match", 0.25)
  }
}
