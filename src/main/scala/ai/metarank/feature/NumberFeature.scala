package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.SingleDim
import ai.metarank.model.Event.RankItem
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, FieldName, Key, MValue, ScopeType}
import ai.metarank.model.Field.NumberField
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.Scalar.SDouble
import ai.metarank.model.Write.Put
import ai.metarank.util.Logging
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import scala.concurrent.duration._

case class NumberFeature(schema: NumberFeatureSchema) extends ItemFeature with Logging {
  override def dim = SingleDim

  private val conf = ScalarConfig(
    scope = schema.scope,
    name = schema.name,
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event, store: Persistence): IO[Iterable[Put]] = IO {
    for {
      key   <- writeKey(event, conf)
      field <- event.fields.find(_.name == schema.field.field)
      numberField <- field match {
        case n: NumberField => Some(n)
        case other =>
          logger.warn(s"field extractor ${schema.name} expects a number field, but got $other in event $event")
          None
      }
    } yield {
      Put(key, event.timestamp, SDouble(numberField.value))
    }
  }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = conf.readKeys(event)

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: RankItem
  ): MValue = {
    val result = for {
      key <- readKey(request, conf, id.id)
      value <- features.get(key) match {
        case Some(ScalarValue(_, _, SDouble(value))) => Some(SingleValue(schema.name, value))
        case _                                       => None
      }
    } yield {
      value
    }
    result.getOrElse(SingleValue.missing(schema.name))
  }

  override def values(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      mode: BaseFeature.ValueMode
  ): List[MValue] = {
    request.items.toList.map { item =>
      {
        val fromField = item.fields.collectFirst {
          case NumberField(name, value) if name == schema.field.field => value
        }
        fromField match {
          case Some(fieldOverride) => SingleValue(schema.name, fieldOverride)
          case None                => value(request, features, item)
        }
      }
    }
  }

}

object NumberFeature {
  import ai.metarank.util.DurationJson._
  case class NumberFeatureSchema(
                                  name: FeatureName,
                                  field: FieldName,
                                  scope: ScopeType,
                                  refresh: Option[FiniteDuration] = None,
                                  ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  implicit val nfDecoder: Decoder[NumberFeatureSchema] = Decoder
    .instance(c =>
      for {
        name <- c.downField("name").as[FeatureName]
        field <- c.downField("source").as[FieldName] match {
          case Left(_)      => c.downField("field").as[FieldName]
          case Right(value) => Right(value)
        }
        scope   <- c.downField("scope").as[ScopeType]
        refresh <- c.downField("refresh").as[Option[FiniteDuration]]
        ttl     <- c.downField("ttl").as[Option[FiniteDuration]]
      } yield {
        NumberFeatureSchema(name, field, scope, refresh, ttl)
      }
    )
    .withErrorMessage("cannot parse a feature definition of type 'number'")

  implicit val nfEncoder: Encoder[NumberFeatureSchema] = deriveEncoder
}
