package ai.metarank.feature

import ai.metarank.feature.BaseFeature.{ItemFeature, ValueMode}
import ai.metarank.feature.RandomFeature.RandomFeatureSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.SingleDim
import ai.metarank.model.Event.RankItem
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, Key, MValue, ScopeType}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.Write.Put
import cats.effect.IO
import io.circe.Codec
import io.circe.generic.semiauto._

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

case class RandomFeature(schema: RandomFeatureSchema) extends ItemFeature {
  override def dim = SingleDim

  override def states: List[FeatureConfig] = Nil

  override def writes(event: Event, store: Persistence): IO[Iterable[Put]] = IO.pure(Nil)

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = Nil

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: RankItem
  ): MValue = SingleValue(schema.name, Random.nextDouble())

}

object RandomFeature {
  case class RandomFeatureSchema(name: FeatureName) extends FeatureSchema {
    lazy val refresh: Option[FiniteDuration] = None
    lazy val ttl: Option[FiniteDuration]     = None
    lazy val scope: ScopeType                = ItemScopeType
  }

  implicit val positionSchemaCodec: Codec[RandomFeatureSchema] = deriveCodec

}
