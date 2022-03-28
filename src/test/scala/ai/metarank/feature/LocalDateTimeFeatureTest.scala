package ai.metarank.feature

import ai.metarank.feature.LocalDateTimeFeature.{
  DateTimeMapper,
  DayOfWeek,
  LocalDateTimeSchema,
  MonthOfYear,
  Second,
  TimeOfDay,
  Year
}
import ai.metarank.model.Field.StringField
import ai.metarank.model.{FieldName, MValue}
import ai.metarank.model.FieldName.Ranking
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.TestRankingEvent
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

class LocalDateTimeFeatureTest extends AnyFlatSpec with Matchers {
  lazy val timeofday = LocalDateTimeFeature(LocalDateTimeSchema("x", FieldName(Ranking, "localts"), TimeOfDay))
  val now            = ZonedDateTime.of(2022, 3, 28, 12, 0, 0, 0, ZoneId.of("UTC+2"))

  it should "complain on improper source event type" in {
    val decoded = parse("name: foo\nsource: metadata.foo\nparse: time_of_day").flatMap(_.as[LocalDateTimeSchema])
    decoded shouldBe a[Left[_, _]]
  }

  it should "decode ranking as source event type" in {
    val decoded = parse("name: foo\nsource: ranking.foo\nparse: time_of_day").flatMap(_.as[LocalDateTimeSchema])
    decoded shouldBe Right(LocalDateTimeSchema("foo", FieldName(Ranking, "foo"), TimeOfDay))
  }

  it should "ignore on format errors" in {
    val result =
      timeofday.value(TestRankingEvent(List("p1")).copy(fields = List(StringField("localts", "now"))), Map.empty)
    result shouldBe SingleValue("x", 0.0)
  }

  it should "ignore on missing field" in {
    val result = timeofday.value(TestRankingEvent(List("p1")), Map.empty)
    result shouldBe SingleValue("x", 0.0)
  }

  it should "parse time of day" in {
    value(now, TimeOfDay) shouldBe 12.0
  }

  it should "parse day of week" in {
    value(now, DayOfWeek) shouldBe 1.0
  }

  it should "parse month of year" in {
    value(now, MonthOfYear) shouldBe 3.0
  }

  it should "parse year" in {
    value(now, Year) shouldBe 2022.0
  }

  it should "parse second" in {
    value(now, Second) shouldBe 1648461600.0
  }

  def value(ts: ZonedDateTime, mapper: DateTimeMapper): Double = {
    lazy val feature = LocalDateTimeFeature(LocalDateTimeSchema("x", FieldName(Ranking, "localts"), mapper))
    val result =
      feature.value(
        TestRankingEvent(List("p1"))
          .copy(fields = List(StringField("localts", DateTimeFormatter.ISO_DATE_TIME.format(ts)))),
        Map.empty
      )
    result match {
      case SingleValue(_, value)       => value
      case MValue.VectorValue(_, _, _) => throw new IllegalStateException("wtf")
    }
  }
}
