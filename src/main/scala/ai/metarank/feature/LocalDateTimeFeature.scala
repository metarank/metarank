package ai.metarank.feature

import ai.metarank.feature.BaseFeature.RankingStatelessFeature
import ai.metarank.feature.LocalDateTimeFeature.LocalDateTimeSchema
import ai.metarank.model.Field.StringField
import ai.metarank.model.FieldName.Ranking
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model._
import ai.metarank.util.Logging
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.Write.Put
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key}

import java.time.format.DateTimeFormatter
import java.time.{Duration, ZonedDateTime}
import scala.util.{Failure, Success, Try}

case class LocalDateTimeFeature(schema: LocalDateTimeSchema) extends RankingStatelessFeature with Logging {
  override def dim: Int                            = 1
  override def states: List[FeatureConfig]         = Nil
  override def fields                              = Nil
  override def writes(event: Event): Iterable[Put] = Nil

  override def value(
      request: Event.RankingEvent,
      state: Map[Key, FeatureValue]
  ): MValue = {

    request.fieldsMap.get(schema.source.field) match {
      case Some(StringField(_, value)) =>
        Try(ZonedDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME)) match {
          case Success(datetime) =>
            SingleValue(schema.name, schema.parse.map(datetime))
          case Failure(ex) =>
            logger.warn(s"cannot parse field '$value' as an ISO DateTime", ex)
            SingleValue(schema.name, 0.0)
        }
      case _ =>
        SingleValue(schema.name, 0.0)
    }
  }
}

object LocalDateTimeFeature {
  sealed trait DateTimeMapper {
    def map(ts: ZonedDateTime): Double
  }
  case object TimeOfDay extends DateTimeMapper {
    lazy val SECONDS_IN_HOUR = Duration.ofHours(1).getSeconds.toDouble
    override def map(ts: ZonedDateTime): Double = {
      val second = ts.toLocalTime.toSecondOfDay
      second / SECONDS_IN_HOUR
    }
  }
  case object DayOfWeek extends DateTimeMapper {
    override def map(ts: ZonedDateTime): Double = {
      ts.getDayOfWeek.getValue.toDouble
    }
  }
  case object MonthOfYear extends DateTimeMapper {
    override def map(ts: ZonedDateTime): Double = {
      ts.getMonth.getValue.toDouble
    }
  }
  case object Year extends DateTimeMapper {
    override def map(ts: ZonedDateTime): Double = {
      ts.getYear.toDouble
    }
  }
  case object Second extends DateTimeMapper {
    override def map(ts: ZonedDateTime): Double = {
      ts.toEpochSecond.toDouble
    }
  }
  case class LocalDateTimeSchema(
      name: String,
      source: FieldName,
      parse: DateTimeMapper
  ) extends FeatureSchema {
    override val refresh = None
    override val ttl     = None
    override val scope   = FeatureScope.SessionScope
  }

  implicit val tdMapperDecoder: Decoder[DateTimeMapper] = Decoder.decodeString.emapTry {
    case "time_of_day"   => Success(TimeOfDay)
    case "day_of_week"   => Success(DayOfWeek)
    case "month_of_year" => Success(MonthOfYear)
    case "year"          => Success(Year)
    case "second"        => Success(Second)
    case other           => Failure(new IllegalArgumentException(s"parsing method $other is not supported"))
  }

  implicit val timeDayDecoder: Decoder[LocalDateTimeSchema] =
    deriveDecoder[LocalDateTimeSchema].ensure(_.source.event == Ranking, "can only work with ranking event fields")

}
