package ai.metarank.feature.time

import ai.metarank.feature.time.TimeOfDayFeature.TimeOfDaySchema
import ai.metarank.model.Field.StringField
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.Ranking
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.TestRankingEvent
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser._
import io.circe.yaml.parser.parse
import io.circe.syntax._

import java.time.{ZoneId, ZonedDateTime}

class TimeOfDayFeatureTest extends AnyFlatSpec with Matchers {
  lazy val feature = TimeOfDayFeature(TimeOfDaySchema("timeofday", FieldName(Ranking, "localts")))
  it should "complain on improper source event type" in {
    val decoded = parse("name: foo\nsource: metadata.foo").flatMap(_.as[TimeOfDaySchema])
    decoded shouldBe a[Left[_, _]]
  }

  it should "decode ranking as source event type" in {
    val decoded = parse("name: foo\nsource: ranking.foo").flatMap(_.as[TimeOfDaySchema])
    decoded shouldBe Right(TimeOfDaySchema("foo", FieldName(Ranking, "foo")))
  }

  it should "emit properly parsed timestamps" in {
    val now    = ZonedDateTime.of(2022, 3, 28, 12, 0, 0, 0, ZoneId.of("UTC+2"))
    val ts     = feature.format.format(now)
    val result = feature.value(TestRankingEvent(List("p1")).copy(fields = List(StringField("localts", ts))), Map.empty)
    result shouldBe SingleValue("timeofday", 0.5)
  }

  it should "ignore on format errors" in {
    val result =
      feature.value(TestRankingEvent(List("p1")).copy(fields = List(StringField("localts", "now"))), Map.empty)
    result shouldBe SingleValue("timeofday", 0.0)
  }

  it should "ignore on missing field" in {
    val result = feature.value(TestRankingEvent(List("p1")), Map.empty)
    result shouldBe SingleValue("timeofday", 0.0)
  }
}
