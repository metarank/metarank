package ai.metarank.model

import ai.metarank.model.TrainResult.{FeatureStatus, IterationStatus}
import io.circe.Codec
import io.circe.generic.semiauto._

case class TrainResult(iterations: List[IterationStatus], sizeBytes: Long, features: List[FeatureStatus])

object TrainResult {
  case class IterationStatus(id: Int, millis: Long, trainMetric: Double, testMetric: Double)
  case class FeatureStatus(
      name: String,
      weight: FeatureWeight,
      empty: Int,
      nonEmpty: Int,
      percentiles: List[Double]
  )

  implicit val featureStatusCodec: Codec[FeatureStatus]     = deriveCodec
  implicit val iterationStatusCodec: Codec[IterationStatus] = deriveCodec
  implicit val trainResultCodec: Codec[TrainResult]         = deriveCodec
}
