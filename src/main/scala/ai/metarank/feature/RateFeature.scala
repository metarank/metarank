package ai.metarank.feature

import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.model.Event.InteractionEvent
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldName, FieldSchema, ItemId, MValue}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.FeatureConfig.{PeriodRange, PeriodicCounterConfig}
import io.findify.featury.model.Key.{FeatureName, Scope}
import io.findify.featury.model.Write.PeriodicIncrement
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, PeriodicCounterValue, Write}
import shapeless.syntax.typeable._

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

case class RateFeature(schema: RateFeatureSchema) extends MFeature {
  override val dim: Int = schema.periods.size
  val names             = schema.periods.map(period => s"${schema.name}_$period")

  override def keys(request: Event.RankingEvent): Traversable[Key] = for {
    item    <- request.items
    feature <- List(top.name.value, bottom.name.value)
  } yield {
    keyOf(ItemScope.value, item.id.value, feature, request.tenant)
  }

  val top = PeriodicCounterConfig(
    scope = Scope(schema.scope.value),
    name = FeatureName(s"${schema.name}_${schema.top}"),
    period = schema.bucket,
    sumPeriodRanges = schema.periods.map(p => PeriodRange(p, 0)),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  val bottom = PeriodicCounterConfig(
    scope = Scope(schema.scope.value),
    name = FeatureName(s"${schema.name}_${schema.bottom}"),
    period = schema.bucket,
    sumPeriodRanges = schema.periods.map(p => PeriodRange(p, 0)),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def states: List[FeatureConfig] = List(top, bottom)

  override def fields: List[FieldSchema] = Nil

  override def writes(event: Event): Traversable[Write] = event match {
    case e: InteractionEvent if e.`type` == schema.top =>
      Some(PeriodicIncrement(keyOf(schema.scope.value, e.item.value, top.name.value, event.tenant), event.timestamp, 1))
    case e: InteractionEvent if e.`type` == schema.bottom =>
      Some(
        PeriodicIncrement(keyOf(schema.scope.value, e.item.value, bottom.name.value, event.tenant), event.timestamp, 1)
      )
    case _ => None
  }

  override def value(request: Event.RankingEvent, state: Map[Key, FeatureValue], id: ItemId): MValue = {
    val result = for {
      topValue    <- state.get(keyOf(schema.scope, id, top.name, request.tenant))
      bottomValue <- state.get(keyOf(schema.scope, id, bottom.name, request.tenant))
      topNum      <- topValue.cast[PeriodicCounterValue]
      bottomNum   <- bottomValue.cast[PeriodicCounterValue]
    } yield {
      val values = topNum.values.zip(bottomNum.values).map(x => x._1.value / x._2.value.toDouble).toArray
      VectorValue(names, values, dim)
    }
    result.getOrElse(VectorValue.empty(names, dim))
  }
}

object RateFeature {
  import ai.metarank.util.DurationJson._
  case class RateFeatureSchema(
      name: String,
      top: String,
      bottom: String,
      bucket: FiniteDuration,
      periods: List[Int],
      scope: FeatureScope,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  implicit val rateSchema: Decoder[RateFeatureSchema] = deriveDecoder
}
