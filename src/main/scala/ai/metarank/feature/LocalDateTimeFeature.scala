package ai.metarank.feature

import ai.metarank.feature.BaseFeature.RankingFeature
import ai.metarank.feature.LocalDateTimeFeature.LocalDateTimeSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.SingleDim
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Field.StringField
import ai.metarank.model.FieldName.EventType
import ai.metarank.model.FieldName.EventType.Ranking
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.ScopeType.SessionScopeType
import ai.metarank.model.Write.Put
import ai.metarank.model.*
import ai.metarank.util.Logging
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import java.time.format.DateTimeFormatter
import java.time.{Duration, Instant, ZoneId, ZonedDateTime}
import scala.util.{Failure, Success, Try}

case class LocalDateTimeFeature(schema: LocalDateTimeSchema) extends RankingFeature with Logging {
  override def dim                                                         = SingleDim
  override def states: List[FeatureConfig]                                 = Nil
  override def writes(event: Event, store: Persistence): IO[Iterable[Put]] = IO.pure(Nil)

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = Nil
  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue]
  ): MValue = {
    schema.source match {
      case FieldName(Ranking, "timestamp") =>
        val instant = Instant.ofEpochMilli(request.timestamp.ts)
        val dt      = ZonedDateTime.ofInstant(instant, ZoneId.of("UTC"))
        SingleValue(schema.name, schema.parse.map(dt))
      case _ =>
        request.fieldsMap.get(schema.source.field) match {
          case Some(StringField(_, value)) =>
            Try(ZonedDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME)) match {
              case Success(datetime) =>
                SingleValue(schema.name, schema.parse.map(datetime))
              case Failure(ex) =>
                logger.warn(s"cannot parse field '$value' as an ISO DateTime", ex)
                SingleValue.missing(schema.name)
            }
          case _ =>
            SingleValue.missing(schema.name)
        }

    }
  }
}

object LocalDateTimeFeature {
  enum DateTimeMapper(val name: String) {
    case TimeOfDay   extends DateTimeMapper("time_of_day")
    case DayOfWeek   extends DateTimeMapper("day_of_week")
    case MonthOfYear extends DateTimeMapper("month_of_year")
    case Year        extends DateTimeMapper("year")
    case Second      extends DateTimeMapper("second")

    def map(ts: ZonedDateTime): Double = this match {
      case TimeOfDay   => ts.toLocalTime.toSecondOfDay / DateTimeMapper.SECONDS_IN_HOUR
      case DayOfWeek   => ts.getDayOfWeek.getValue.toDouble
      case MonthOfYear => ts.getMonth.getValue.toDouble
      case Year        => ts.getYear.toDouble
      case Second      => ts.toEpochSecond.toDouble
    }
  }
  object DateTimeMapper {
    private lazy val SECONDS_IN_HOUR = Duration.ofHours(1).getSeconds.toDouble
  }
  // enum cases live in the DateTimeMapper namespace: re-exported to keep the historical flat paths working
  export DateTimeMapper.{TimeOfDay, DayOfWeek, MonthOfYear, Year, Second}
  case class LocalDateTimeSchema(
      name: FeatureName,
      source: FieldName,
      parse: DateTimeMapper
  ) extends FeatureSchema {
    override val refresh = None
    override val ttl     = None
    override val scope   = SessionScopeType

    override def create(): IO[BaseFeature] = IO.pure(LocalDateTimeFeature(this))
  }

  given tdMapperDecoder: Decoder[DateTimeMapper] = Decoder.decodeString.emapTry {
    case TimeOfDay.name   => Success(TimeOfDay)
    case DayOfWeek.name   => Success(DayOfWeek)
    case MonthOfYear.name => Success(MonthOfYear)
    case Year.name        => Success(Year)
    case Second.name      => Success(Second)
    case other            => Failure(new IllegalArgumentException(s"parsing method $other is not supported"))
  }

  given tdMapperEncoder: Encoder[DateTimeMapper] = Encoder.encodeString.contramap(_.name)

  given timeDayDecoder: Decoder[LocalDateTimeSchema] =
    deriveDecoder[LocalDateTimeSchema]
      .ensure(
        _.source.event == EventType.Ranking,
        "can only work with ranking event fields"
      )
      .withErrorMessage("cannot parse a feature definition of type 'local_time'")

  given timeDayEncoder: Encoder[LocalDateTimeSchema] = deriveEncoder
}
