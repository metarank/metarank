package ai.metarank.model

import ai.metarank.model.TrainResult.{FeatureStatus, IterationStatus}
import io.circe.Codec
import io.circe.generic.semiauto.*

case class TrainResult(features: List[FeatureStatus])

object TrainResult {
  def empty = TrainResult(Nil)
  case class IterationStatus(id: Int, millis: Long, trainMetric: Double, testMetric: Double)
  case class FeatureStatus(
      name: String,
      weight: FeatureWeight
  ) {
    def asPrintString = {
      val w = weight match {
        case FeatureWeight.SingularWeight(value) => value.toString
        case FeatureWeight.VectorWeight(values)  => values.mkString("[", ",", "]")
      }
      s"$name: weight=$w"
    }
  }

  given featureStatusCodec: Codec[FeatureStatus]     = deriveCodec
  given iterationStatusCodec: Codec[IterationStatus] = deriveCodec
  given trainResultCodec: Codec[TrainResult]         = deriveCodec
}
