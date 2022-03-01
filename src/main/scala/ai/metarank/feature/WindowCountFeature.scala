package ai.metarank.feature

import ai.metarank.feature.MetaFeature.StatelessFeature
import ai.metarank.feature.WindowCountFeature.WindowCountSchema
import ai.metarank.model.Event.InteractionEvent
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldSchema, ItemId, MValue}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.FeatureConfig.{PeriodRange, PeriodicCounterConfig}
import io.findify.featury.model.Key.FeatureName
import io.findify.featury.model.Write.PeriodicIncrement
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, PeriodicCounterValue, Write}
import shapeless.syntax.typeable.typeableOps

import scala.concurrent.duration._

case class WindowCountFeature(schema: WindowCountSchema) extends StatelessFeature {
  override val dim: Int = schema.periods.size
  val names             = schema.periods.map(period => s"${schema.name}_$period")

  val conf = PeriodicCounterConfig(
    scope = schema.scope.scope,
    name = FeatureName(schema.name),
    period = schema.bucket,
    sumPeriodRanges = schema.periods.map(p => PeriodRange(p, 0)),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def states: List[FeatureConfig] = List(conf)

  override def fields: List[FieldSchema] = Nil

  override def writes(event: Event): Traversable[Write] = event match {
    case e: InteractionEvent if e.`type` == schema.interaction =>
      Some(
        PeriodicIncrement(
          keyOf(schema.scope.scope.name, e.item.value, conf.name.value, event.tenant),
          event.timestamp,
          1
        )
      )
    case _ => None
  }

  override def value(
      request: Event.RankingEvent,
      state: Map[Key, FeatureValue],
      id: ItemId
  ): MValue = {
    val result = for {
      value    <- state.get(keyOf(schema.scope, id, conf.name, request.tenant))
      valueNum <- value.cast[PeriodicCounterValue] if valueNum.values.size == dim
    } yield {
      VectorValue(names, valueNum.values.map(_.value.toDouble).toArray, dim)
    }
    result.getOrElse(VectorValue.empty(names, dim))
  }

}

object WindowCountFeature {
  import ai.metarank.util.DurationJson._
  case class WindowCountSchema(
      name: String,
      interaction: String,
      bucket: FiniteDuration,
      periods: List[Int],
      scope: FeatureScope,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  implicit val windowCountDecoder: Decoder[WindowCountSchema] = deriveDecoder
}
