package ai.metarank.feature

import ai.metarank.feature.ItemAgeFeature.ItemAgeSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event.RankItem
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Field.{NumberField, StringField}
import ai.metarank.model.{FieldName, Key, Timestamp}
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.Scalar.SDouble
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.Write.Put
import ai.metarank.util.{TestItemEvent, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.{ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

class ItemAgeFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {
  lazy val feature   = ItemAgeFeature(ItemAgeSchema(FeatureName("itemage"), FieldName(Item, "updated_at")))
  lazy val updatedAt = ZonedDateTime.of(2022, 3, 1, 0, 0, 0, 0, ZoneId.of("UTC+2"))
  lazy val now       = ZonedDateTime.of(2022, 3, 28, 0, 0, 0, 0, ZoneId.of("UTC+2"))

  it should "make puts from iso timestamps" in {
    val event = TestItemEvent(
      "p1",
      List(StringField("updated_at", updatedAt.format(DateTimeFormatter.ISO_DATE_TIME)))
    ).copy(timestamp = Timestamp(updatedAt.toInstant.toEpochMilli))

    val puts = feature.writes(event).unsafeRunSync().toList
    puts shouldBe List(
      Put(
        Key(ItemScope(ItemId("p1")), FeatureName("itemage")),
        Timestamp(updatedAt.toInstant.toEpochMilli),
        SDouble(updatedAt.toEpochSecond.toDouble)
      )
    )
  }

  it should "make puts from unixtime" in {
    val event = TestItemEvent(
      "p1",
      List(NumberField("updated_at", updatedAt.toEpochSecond.toDouble))
    ).copy(timestamp = Timestamp(updatedAt.toInstant.toEpochMilli))

    val puts = feature.writes(event).unsafeRunSync().toList
    puts shouldBe List(
      Put(
        Key(ItemScope(ItemId("p1")), FeatureName("itemage")),
        Timestamp(updatedAt.toInstant.toEpochMilli),
        SDouble(updatedAt.toEpochSecond.toDouble)
      )
    )
  }
  it should "make puts from event timestamp" in {
    lazy val feature   = ItemAgeFeature(ItemAgeSchema(FeatureName("itemage"), FieldName(Item, "timestamp")))

    val event = TestItemEvent("p1").copy(timestamp = Timestamp(updatedAt.toInstant.toEpochMilli))

    val puts = feature.writes(event).unsafeRunSync().toList
    puts shouldBe List(
      Put(
        Key(ItemScope(ItemId("p1")), FeatureName("itemage")),
        Timestamp(updatedAt.toInstant.toEpochMilli),
        SDouble(updatedAt.toEpochSecond.toDouble)
      )
    )
  }

  it should "make puts from unixtime string" in {
    val event = TestItemEvent(
      "p1",
      List(StringField("updated_at", updatedAt.toEpochSecond.toString))
    ).copy(timestamp = Timestamp(updatedAt.toInstant.toEpochMilli))

    val puts = feature.writes(event).unsafeRunSync().toList
    puts shouldBe List(
      Put(
        Key(ItemScope(ItemId("p1")), FeatureName("itemage")),
        Timestamp(updatedAt.toInstant.toEpochMilli),
        SDouble(updatedAt.toEpochSecond.toDouble)
      )
    )
  }

  it should "compute item age" in {
    val key   = Key(ItemScope(ItemId("p1")), FeatureName("itemage"))
    val nowts = Timestamp(now.toInstant.toEpochMilli)
    val result = feature.value(
      TestRankingEvent(List("p1")).copy(timestamp = nowts),
      Map(
        key -> ScalarValue(key, Timestamp(updatedAt.toInstant.toEpochMilli), SDouble(updatedAt.toEpochSecond.toDouble))
      ),
      RankItem(ItemId("p1"))
    )
    result shouldBe SingleValue(FeatureName("itemage"), 2332800.0)
  }

  it should "process events" in {
    val event = TestItemEvent(
      "p1",
      List(StringField("updated_at", updatedAt.format(DateTimeFormatter.ISO_DATE_TIME)))
    ).copy(timestamp = Timestamp(updatedAt.toInstant.toEpochMilli))
    val result = process(
      events = List(event),
      schema = feature.schema,
      request = TestRankingEvent(List("p1")).copy(timestamp = Timestamp(now.toInstant.toEpochMilli))
    )
    result shouldBe List(List(SingleValue(FeatureName("itemage"), 2332800.0)))
  }
}
