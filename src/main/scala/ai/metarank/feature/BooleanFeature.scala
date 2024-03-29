package ai.metarank.feature

import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.SingleDim
import ai.metarank.model.Event.RankItem
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Field.{BooleanField, NumberField}
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, FieldName, Key, MValue, ScopeType, Write}
import ai.metarank.util.Logging
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scalar.SBoolean
import ai.metarank.model.Write.Put
import cats.effect.IO

import scala.concurrent.duration._
import shapeless.syntax.typeable._

case class BooleanFeature(schema: BooleanFeatureSchema) extends ItemFeature with Logging {
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
      fieldValue <- field match {
        case b: BooleanField => Some(b)
        case other =>
          logger.warn(s"field extractor ${schema.name} expects a boolean, but got $other in event $event")
          None
      }
    } yield {
      Put(key, event.timestamp, SBoolean(fieldValue.value))
    }
  }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = conf.readKeys(event)

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: RankItem
  ): MValue = {
    val result = for {
      key    <- readKey(request, conf, id.id)
      value  <- features.get(key)
      scalar <- value.cast[ScalarValue]
      bool   <- scalar.value.cast[SBoolean]
    } yield {
      SingleValue(schema.name, if (bool.value) 1.0 else 0.0)
    }
    result.getOrElse(SingleValue.missing(schema.name))
  }
}

object BooleanFeature {
  import ai.metarank.util.DurationJson._
  case class BooleanFeatureSchema(
      name: FeatureName,
      field: FieldName,
      scope: ScopeType,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema {
    override def create(): IO[BaseFeature] = IO.pure(BooleanFeature(this))
  }

  implicit val boolSchemaDecoder: Decoder[BooleanFeatureSchema] = Decoder
    .instance(c =>
      for {
        name <- c.downField("name").as[FeatureName]
        field <- c.downField("field").as[FieldName] match {
          case Left(_)      => c.downField("source").as[FieldName]
          case Right(value) => Right(value)
        }
        scope   <- c.downField("scope").as[ScopeType]
        refresh <- c.downField("refresh").as[Option[FiniteDuration]]
        ttl     <- c.downField("ttl").as[Option[FiniteDuration]]
      } yield {
        BooleanFeatureSchema(name, field, scope, refresh, ttl)
      }
    )
    .withErrorMessage("cannot parse a feature definition of type 'boolean'")

  implicit val boolSchemaEncoder: Encoder[BooleanFeatureSchema] = deriveEncoder[BooleanFeatureSchema]
}
