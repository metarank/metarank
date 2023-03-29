package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.WindowInteractionCountFeature.WindowInteractionCountSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.VectorDim
import ai.metarank.model.Event.{InteractionEvent, RankItem}
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.PeriodicCounterFeature.{PeriodRange, PeriodicCounterConfig}
import ai.metarank.model.FeatureValue.PeriodicCounterValue
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.Write.PeriodicIncrement
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, FieldName, Key, MValue, ScopeType, Write}
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import shapeless.syntax.typeable.typeableOps

import scala.concurrent.duration._

case class WindowInteractionCountFeature(schema: WindowInteractionCountSchema) extends ItemFeature {
  override val dim = VectorDim(schema.periods.size)

  val conf = PeriodicCounterConfig(
    scope = schema.scope,
    name = schema.name,
    period = schema.bucket,
    sumPeriodRanges = schema.periods.map(p => PeriodRange(p, 0)),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event, store: Persistence): IO[Iterable[Write]] = IO {
    for {
      key <- writeKey(event, conf)
      inc <- event match {
        case e: InteractionEvent if e.`type` == schema.interaction => Some(PeriodicIncrement(key, event.timestamp, 1))
        case _                                                     => None
      }
    } yield {
      inc
    }
  }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = conf.readKeys(event)

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: RankItem
  ): MValue = {
    val result = for {
      key      <- readKey(request, conf, id.id)
      value    <- features.get(key)
      valueNum <- value.cast[PeriodicCounterValue] if valueNum.values.length == dim.dim
    } yield {
      VectorValue(schema.name, valueNum.values.map(_.value.toDouble), dim)
    }
    result.getOrElse(VectorValue.missing(schema.name, dim))
  }

}

object WindowInteractionCountFeature {
  import ai.metarank.util.DurationJson._
  case class WindowInteractionCountSchema(
      name: FeatureName,
      interaction: String,
      bucket: FiniteDuration,
      periods: List[Int],
      scope: ScopeType,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema {
    override def create(): IO[BaseFeature] = IO.pure(WindowInteractionCountFeature(this))
  }

  implicit val windowCountDecoder: Decoder[WindowInteractionCountSchema] =
    deriveDecoder[WindowInteractionCountSchema].withErrorMessage(
      "cannot parse a feature definition of type 'window_count'"
    )

  implicit val windowCountEncoder: Encoder[WindowInteractionCountSchema] = deriveEncoder
}
