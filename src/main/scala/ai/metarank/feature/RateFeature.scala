package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.VectorDim
import ai.metarank.model.Event.{InteractionEvent, RankItem}
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.PeriodicCounterFeature.{PeriodRange, PeriodicCounterConfig}
import ai.metarank.model.FeatureValue.PeriodicCounterValue
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.Scope.{GlobalScope, ItemScope}
import ai.metarank.model.ScopeType.{GlobalScopeType, ItemScopeType}
import ai.metarank.model.Write.PeriodicIncrement
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, FieldName, Key, MValue, ScopeType, Write}
import cats.effect.IO
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}
import shapeless.syntax.typeable._

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

case class RateFeature(schema: RateFeatureSchema) extends ItemFeature {
  override val dim = VectorDim(schema.periods.size)

  val topGlobal = PeriodicCounterConfig(
    scope = GlobalScopeType,
    name = FeatureName(s"${schema.name.value}_${schema.top}_norm"),
    period = schema.bucket,
    sumPeriodRanges = schema.periods.map(p => PeriodRange(p, 0)),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  val bottomGlobal = PeriodicCounterConfig(
    scope = GlobalScopeType,
    name = FeatureName(s"${schema.name.value}_${schema.bottom}_norm"),
    period = schema.bucket,
    sumPeriodRanges = schema.periods.map(p => PeriodRange(p, 0)),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  val topItem = PeriodicCounterConfig(
    scope = schema.scope,
    name = FeatureName(s"${schema.name.value}_${schema.top}"),
    period = schema.bucket,
    sumPeriodRanges = schema.periods.map(p => PeriodRange(p, 0)),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  val bottomItem = PeriodicCounterConfig(
    scope = schema.scope,
    name = FeatureName(s"${schema.name.value}_${schema.bottom}"),
    period = schema.bucket,
    sumPeriodRanges = schema.periods.map(p => PeriodRange(p, 0)),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def states: List[FeatureConfig] = List(topItem, bottomItem, topGlobal, bottomGlobal)

  override def writes(event: Event): IO[Iterable[Write]] = IO {
    event match {
      case e: InteractionEvent if e.`type` == schema.top =>
        schema.normalize match {
          case Some(_) =>
            List(
              PeriodicIncrement(Key(ItemScope(e.item), topItem.name), event.timestamp, 1),
              PeriodicIncrement(Key(GlobalScope, topGlobal.name), event.timestamp, 1)
            )
          case None =>
            List(PeriodicIncrement(Key(ItemScope(e.item), topItem.name), event.timestamp, 1))
        }

      case e: InteractionEvent if e.`type` == schema.bottom =>
        schema.normalize match {
          case Some(_) =>
            List(
              PeriodicIncrement(Key(ItemScope(e.item), bottomItem.name), event.timestamp, 1),
              PeriodicIncrement(Key(GlobalScope, bottomGlobal.name), event.timestamp, 1)
            )
          case None =>
            List(PeriodicIncrement(Key(ItemScope(e.item), bottomItem.name), event.timestamp, 1))
        }

      case _ => None
    }
  }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = {
    topItem.readKeys(event) ++ bottomItem.readKeys(event) ++ topGlobal.readKeys(event) ++ bottomGlobal.readKeys(event)
  }

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: RankItem
  ): MValue = {
    schema.normalize match {
      case None =>
        val result = for {
          topValue    <- features.get(Key(ItemScope(id.id), topItem.name))
          bottomValue <- features.get(Key(ItemScope(id.id), bottomItem.name))
          topNum      <- topValue.cast[PeriodicCounterValue] if topNum.values.length == dim.dim
          bottomNum   <- bottomValue.cast[PeriodicCounterValue] if bottomNum.values.length == dim.dim
        } yield {
          val values = new Array[Double](dim.dim)
          var i      = 0
          while (i < dim.dim) {
            values(i) = topNum.values(i).value / bottomNum.values(i).value.toDouble
            i += 1
          }
          VectorValue(schema.name, values, dim)
        }
        result.getOrElse(VectorValue.missing(schema.name, dim))
      case Some(norm) =>
        val result = for {
          topValue          <- features.get(Key(ItemScope(id.id), topItem.name))
          bottomValue       <- features.get(Key(ItemScope(id.id), bottomItem.name))
          topGlobalValue    <- features.get(Key(GlobalScope, topGlobal.name))
          bottomGlobalValue <- features.get(Key(GlobalScope, bottomGlobal.name))
          topNum            <- topValue.cast[PeriodicCounterValue] if topNum.values.length == dim.dim
          bottomNum         <- bottomValue.cast[PeriodicCounterValue] if bottomNum.values.length == dim.dim
          topGlobalNum      <- topGlobalValue.cast[PeriodicCounterValue] if topGlobalNum.values.length == dim.dim
          bottomGlobalNum   <- bottomGlobalValue.cast[PeriodicCounterValue] if bottomGlobalNum.values.length == dim.dim
        } yield {
          val values = new Array[Double](dim.dim)
          var i      = 0
          while (i < dim.dim) {
            values(i) = (norm.weight + topNum.values(i).value) / (norm.weight * (bottomGlobalNum
              .values(i)
              .value / topGlobalNum.values(i).value) + bottomNum.values(i).value)
            i += 1
          }

          VectorValue(schema.name, values, dim)
        }
        result.getOrElse(VectorValue.missing(schema.name, dim))
    }
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
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None,
      normalize: Option[NormalizeSchema] = None
  ) extends FeatureSchema {
    val scope = ItemScopeType
  }

  case class NormalizeSchema(weight: Double)

  implicit val normalizeSchemaCodec: Codec[NormalizeSchema] = deriveCodec

  implicit val rateSchemaDecoder: Decoder[RateFeatureSchema] =
    deriveDecoder[RateFeatureSchema]
      .withErrorMessage("cannot parse a feature definition of type 'rate'")

  implicit val rateSchemaEncoder: Encoder[RateFeatureSchema] = deriveEncoder
}
