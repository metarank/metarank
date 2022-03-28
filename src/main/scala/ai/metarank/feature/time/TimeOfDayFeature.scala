package ai.metarank.feature.time

import ai.metarank.feature.BaseFeature.RankingStatelessFeature
import ai.metarank.feature.time.TimeOfDayFeature.TimeOfDaySchema
import ai.metarank.model.Field.{NumberField, StringField}
import ai.metarank.model.FieldName.Ranking
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldName, MValue}
import ai.metarank.model.MValue.{SingleValue, VectorValue}
import ai.metarank.util.{Logging, OneHotEncoder}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key}
import io.findify.featury.model.Write.Put

import java.time.{Duration, LocalDateTime, ZonedDateTime}
import java.time.format.DateTimeFormatter
import scala.util.{Failure, Success, Try}

case class TimeOfDayFeature(schema: TimeOfDaySchema) extends RankingStatelessFeature with Logging {
  lazy val format                                  = DateTimeFormatter.ISO_DATE_TIME
  lazy val SECONDS_IN_DAY                          = Duration.ofDays(1).getSeconds.toDouble
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
        Try(ZonedDateTime.parse(value, format)) match {
          case Success(datetime) =>
            val second = datetime.toLocalTime.toSecondOfDay
            SingleValue(schema.name, second / SECONDS_IN_DAY)
          case Failure(ex) =>
            logger.warn(s"cannot parse field '$value' as an ISO DateTime", ex)
            SingleValue(schema.name, 0.0)
        }
      case _ =>
        SingleValue(schema.name, 0.0)

    }
  }
}

object TimeOfDayFeature {
  case class TimeOfDaySchema(
      name: String,
      source: FieldName
  ) extends FeatureSchema {
    override val refresh = None
    override val ttl     = None
    override val scope   = FeatureScope.SessionScope
  }

  implicit val timeDayDecoder: Decoder[TimeOfDaySchema] =
    deriveDecoder[TimeOfDaySchema].ensure(_.source.event == Ranking, "can only work with ranking event fields")
}
