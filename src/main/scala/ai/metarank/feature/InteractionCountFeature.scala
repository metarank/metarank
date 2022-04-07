package ai.metarank.feature

import ai.metarank.feature.InteractionCountFeature.InteractionCountSchema
import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.flow.FieldStore
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, MValue}
import ai.metarank.model.Identifier._
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.Logging
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.FeatureConfig.{CounterConfig, ScalarConfig}
import io.findify.featury.model.Key.{FeatureName, Tenant}
import io.findify.featury.model.Write.{Increment, Put}
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SDouble, ScalarValue}
import shapeless.syntax.typeable._

import scala.concurrent.duration._

case class InteractionCountFeature(schema: InteractionCountSchema) extends ItemFeature with Logging {
  override def dim: Int = 1

  private val conf = CounterConfig(
    scope = schema.scope.scope,
    name = FeatureName(schema.name),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def fields                      = Nil
  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event, fields: FieldStore): Iterable[Increment] = for {
    key <- keyOf(event)
    increment <- event match {
      case interaction: Event.InteractionEvent if interaction.`type` == schema.interaction => Some(1)
      case _                                                                               => None
    }
  } yield {
    Increment(key, event.timestamp, increment)
  }

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: ItemRelevancy
  ): MValue = {
    val result = for {
      key   <- keyOf(request, Some(id.id))
      value <- features.get(key)
    } yield {
      value
    }
    result match {
      case Some(ScalarValue(_, _, SDouble(value))) => SingleValue(schema.name, value)
      case _                                       => SingleValue(schema.name, 0)
    }
  }
}

object InteractionCountFeature {
  import ai.metarank.util.DurationJson._
  case class InteractionCountSchema(
      name: String,
      interaction: String,
      scope: FeatureScope,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  implicit val interCountDecoder: Decoder[InteractionCountSchema] = deriveDecoder
}
