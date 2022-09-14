package ai.metarank.feature

import ai.metarank.feature.InteractionCountFeature.InteractionCountSchema
import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.SingleDim
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.Feature.CounterFeature.CounterConfig
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.FeatureValue.CounterValue
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, Key, MValue, ScopeType}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.Write.Increment
import ai.metarank.util.Logging
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import scala.concurrent.duration._

case class InteractionCountFeature(schema: InteractionCountSchema) extends ItemFeature with Logging {
  override def dim = SingleDim

  private val conf = CounterConfig(
    scope = schema.scope,
    name = schema.name,
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event, store: Persistence): IO[Iterable[Increment]] = IO {
    event match {
      case interaction: Event.InteractionEvent if interaction.`type` == schema.interaction =>
        writeKey(event, conf).map(key => Increment(key, event.timestamp, 1))
      case _ =>
        None
    }
  }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = conf.readKeys(event)

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: ItemRelevancy
  ): MValue = {
    val result = for {
      key   <- readKey(request, conf, id.id)
      value <- features.get(key)
    } yield {
      value
    }
    result match {
      case Some(CounterValue(_, _, value)) => SingleValue(schema.name, value.toDouble)
      case _                               => SingleValue(schema.name, 0.0)
    }
  }
}

object InteractionCountFeature {
  import ai.metarank.util.DurationJson._
  case class InteractionCountSchema(
      name: FeatureName,
      interaction: String,
      scope: ScopeType,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  implicit val interCountDecoder: Decoder[InteractionCountSchema] =
    deriveDecoder[InteractionCountSchema].withErrorMessage(
      "cannot parse a feature definition of type 'interaction_count'"
    )

  implicit val interCountEncoder: Encoder[InteractionCountSchema] = deriveEncoder
}
