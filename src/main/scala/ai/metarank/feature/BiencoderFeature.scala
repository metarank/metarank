package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.BiencoderFeature.BiencoderSchema
import ai.metarank.model.Dimension.SingleDim
import ai.metarank.model.{FeatureSchema, ScopeType}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.util.Logging

import scala.concurrent.duration.FiniteDuration

//case class BiencoderFeature(schema: BiencoderSchema) extends ItemFeature with Logging {
//  override def dim = SingleDim
//}

object BiencoderFeature {
  case class BiencoderSchema(
      name: FeatureName,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema {
    lazy val scope: ScopeType                = ItemScopeType
  }


}
