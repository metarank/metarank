package ai.metarank.feature

import ai.metarank.feature.ItemAgeFeature.ItemAgeSchema
import ai.metarank.flow.FieldStore
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.Field.{NumberField, StringField}
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.{TestItemEvent, TestRankingEvent}
import io.findify.featury.model.{Key, SDouble, ScalarValue, Timestamp}
import io.findify.featury.model.Key.{FeatureName, Scope, Tag, Tenant}
import io.findify.featury.model.Write.Put
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.{ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

class ItemAgeFeatureTest extends AnyFlatSpec with Matchers {
  lazy val feature   = ItemAgeFeature(ItemAgeSchema("itemage", FieldName(Item, "updated_at")))
  lazy val updatedAt = ZonedDateTime.of(2022, 3, 1, 0, 0, 0, 0, ZoneId.of("UTC+2"))
  lazy val now       = ZonedDateTime.of(2022, 3, 28, 0, 0, 0, 0, ZoneId.of("UTC+2"))

  it should "make puts from iso timestamps" in {
    val event = TestItemEvent(
      "p1",
      List(StringField("updated_at", updatedAt.format(DateTimeFormatter.ISO_DATE_TIME)))
    ).copy(timestamp = Timestamp(updatedAt.toInstant.toEpochMilli))

    val puts = feature.writes(event, FieldStore.empty).toList
    puts shouldBe List(
      Put(
        Key(Tag(ItemScope.scope, "p1"), FeatureName("itemage"), Tenant("default")),
        Timestamp(updatedAt.toInstant.toEpochMilli),
        SDouble(updatedAt.toEpochSecond)
      )
    )
  }

  it should "make puts from unixtime" in {
    val event = TestItemEvent(
      "p1",
      List(NumberField("updated_at", updatedAt.toEpochSecond))
    ).copy(timestamp = Timestamp(updatedAt.toInstant.toEpochMilli))

    val puts = feature.writes(event, FieldStore.empty).toList
    puts shouldBe List(
      Put(
        Key(Tag(ItemScope.scope, "p1"), FeatureName("itemage"), Tenant("default")),
        Timestamp(updatedAt.toInstant.toEpochMilli),
        SDouble(updatedAt.toEpochSecond)
      )
    )
  }

  it should "make puts from unixtime string" in {
    val event = TestItemEvent(
      "p1",
      List(StringField("updated_at", updatedAt.toEpochSecond.toString))
    ).copy(timestamp = Timestamp(updatedAt.toInstant.toEpochMilli))

    val puts = feature.writes(event, FieldStore.empty).toList
    puts shouldBe List(
      Put(
        Key(Tag(ItemScope.scope, "p1"), FeatureName("itemage"), Tenant("default")),
        Timestamp(updatedAt.toInstant.toEpochMilli),
        SDouble(updatedAt.toEpochSecond)
      )
    )
  }

  it should "compute item age" in {
    val key   = Key(Tag(ItemScope.scope, "p1"), FeatureName("itemage"), Tenant("default"))
    val nowts = Timestamp(now.toInstant.toEpochMilli)
    val result = feature.value(
      TestRankingEvent(List("p1")).copy(timestamp = nowts),
      Map(key -> ScalarValue(key, Timestamp(updatedAt.toInstant.toEpochMilli), SDouble(updatedAt.toEpochSecond))),
      ItemRelevancy(ItemId("p1"))
    )
    result shouldBe SingleValue("itemage", 2332800.0)
  }
}
