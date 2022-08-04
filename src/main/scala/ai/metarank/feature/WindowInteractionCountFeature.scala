package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.WindowInteractionCountFeature.WindowCountSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event.{InteractionEvent, ItemRelevancy}
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.PeriodicCounter.{PeriodRange, PeriodicCounterConfig}
import ai.metarank.model.FeatureValue.PeriodicCounterValue
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.Write.PeriodicIncrement
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, FieldName, Key, MValue, ScopeType, Write}
import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import shapeless.syntax.typeable.typeableOps

import scala.concurrent.duration._

case class WindowInteractionCountFeature(schema: WindowCountSchema) extends ItemFeature {
  override val dim: Int = schema.periods.size
  val names             = schema.periods.map(period => s"${schema.name}_$period")

  val conf = PeriodicCounterConfig(
    scope = schema.scope,
    name = schema.name,
    period = schema.bucket,
    sumPeriodRanges = schema.periods.map(p => PeriodRange(p, 0)),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def states: List[FeatureConfig] = List(conf)

  override def fields: List[FieldName] = Nil

  override def writes(event: Event, fields: Persistence): IO[Iterable[Write]] = IO {
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
      id: ItemRelevancy
  ): MValue = {
    val result = for {
      key <- readKey(request, conf, id.id)
      value    <- features.get(key)
      valueNum <- value.cast[PeriodicCounterValue] if valueNum.values.size == dim
    } yield {
      VectorValue(names, valueNum.values.map(_.value.toDouble).toArray, dim)
    }
    result.getOrElse(VectorValue.empty(names, dim))
  }

}

object WindowInteractionCountFeature {
  import ai.metarank.util.DurationJson._
  case class WindowCountSchema(
      name: FeatureName,
      interaction: String,
      bucket: FiniteDuration,
      periods: List[Int],
      scope: ScopeType,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  implicit val windowCountDecoder: Decoder[WindowCountSchema] =
    deriveDecoder[WindowCountSchema].withErrorMessage("cannot parse a feature definition of type 'window_count'")
}
