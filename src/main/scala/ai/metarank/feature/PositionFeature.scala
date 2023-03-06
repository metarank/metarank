package ai.metarank.feature

import ai.metarank.feature.BaseFeature.{ItemFeature, ValueMode}
import ai.metarank.feature.PositionFeature.PositionFeatureSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.SingleDim
import ai.metarank.model.Event.RankItem
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, Key, MValue, ScopeType}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.Write.Put
import ai.metarank.util.Logging
import cats.effect.IO
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import scala.concurrent.duration.FiniteDuration

case class PositionFeature(schema: PositionFeatureSchema) extends ItemFeature with Logging {
  override def dim = SingleDim

  override def states: List[FeatureConfig] = Nil

  override def writes(event: Event, store: Persistence): IO[Iterable[Put]] = IO.pure(Nil)

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = Nil

  override def values(request: Event.RankingEvent, features: Map[Key, FeatureValue], mode: ValueMode): List[MValue] = {
    mode match {
      case ValueMode.OnlineInference => request.items.toList.map(_ => SingleValue(schema.name, schema.position))
      case ValueMode.OfflineTraining => request.items.toList.zipWithIndex.map(i => SingleValue(schema.name, i._2))
    }
  }

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: RankItem
  ): MValue = ???

}

object PositionFeature {
  case class PositionFeatureSchema(name: FeatureName, position: Int) extends FeatureSchema {
    lazy val refresh: Option[FiniteDuration] = None
    lazy val ttl: Option[FiniteDuration]     = None
    lazy val scope: ScopeType                = ItemScopeType
  }

  implicit val positionSchemaCodec: Codec[PositionFeatureSchema] = deriveCodec
}
