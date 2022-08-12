package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event.{InteractionEvent, ItemRelevancy}
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.PeriodicCounter.{PeriodRange, PeriodicCounterConfig}
import ai.metarank.model.FeatureValue.PeriodicCounterValue
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.Write.PeriodicIncrement
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, FieldName, Key, MValue, ScopeType, Write}
import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import shapeless.syntax.typeable._

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

case class RateFeature(schema: RateFeatureSchema) extends ItemFeature {
  override val dim: Int = schema.periods.size

  val top = PeriodicCounterConfig(
    scope = schema.scope,
    name = FeatureName(s"${schema.name.value}_${schema.top}"),
    period = schema.bucket,
    sumPeriodRanges = schema.periods.map(p => PeriodRange(p, 0)),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  val bottom = PeriodicCounterConfig(
    scope = schema.scope,
    name = FeatureName(s"${schema.name.value}_${schema.bottom}"),
    period = schema.bucket,
    sumPeriodRanges = schema.periods.map(p => PeriodRange(p, 0)),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def states: List[FeatureConfig] = List(top, bottom)

  override def writes(event: Event, fields: Persistence): IO[Iterable[Write]] = IO {
    event match {
      case e: InteractionEvent if e.`type` == schema.top =>
        Some(
          PeriodicIncrement(
            Key(ItemScope(e.item), top.name),
            event.timestamp,
            1
          )
        )
      case e: InteractionEvent if e.`type` == schema.bottom =>
        Some(
          PeriodicIncrement(
            Key(ItemScope(e.item), bottom.name),
            event.timestamp,
            1
          )
        )
      case _ => None
    }
  }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = top.readKeys(event) ++ bottom.readKeys(event)

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: ItemRelevancy
  ): MValue = {
    val result = for {
      topValue    <- features.get(Key(ItemScope(id.id), top.name))
      bottomValue <- features.get(Key(ItemScope(id.id), bottom.name))
      topNum      <- topValue.cast[PeriodicCounterValue] if topNum.values.size == dim
      bottomNum   <- bottomValue.cast[PeriodicCounterValue] if (bottomNum.values.size == dim)
    } yield {
      val values = topNum.values.zip(bottomNum.values).map(x => x._1.value / x._2.value.toDouble).toArray
      VectorValue(schema.name, values, dim)
    }
    result.getOrElse(VectorValue.empty(schema.name, dim))
  }
}

object RateFeature {
  import ai.metarank.util.DurationJson._
  case class RateFeatureSchema(
      name: FeatureName,
      top: String,
      bottom: String,
      bucket: FiniteDuration,
      periods: List[Int],
      scope: ScopeType,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  implicit val rateSchema: Decoder[RateFeatureSchema] =
    deriveDecoder[RateFeatureSchema].withErrorMessage("cannot parse a feature definition of type 'rate'")
}
