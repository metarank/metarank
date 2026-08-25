package ai.metarank.ml.onnx

import ai.metarank.model.MValue.SingleValue
import io.circe.Decoder

import scala.util.{Failure, Success}

enum Normalize {
  case MinMaxNormalize
  case PositionNormalize
  case NoopNormalize

  def scale(values: List[SingleValue]): List[SingleValue] = this match {
    case MinMaxNormalize =>
      val scores = values.map(_.value).filterNot(_.isNaN)
      (scores.minOption, scores.maxOption) match {
        case (Some(min), Some(max)) =>
          values.map(v => v.copy(value = (v.value - min) / (max - min)))
        case _ => values
      }

    case PositionNormalize =>
      val size = values.size.toDouble
      values.zipWithIndex
        .sortBy(_._1.value)
        .zipWithIndex
        .map { case ((value, origIndex), sortedIndex) =>
          if (value.value.isNaN) {
            value -> origIndex
          } else {
            value.copy(value = sortedIndex / size) -> origIndex
          }

        }
        .sortBy(_._2)
        .map(_._1)

    case NoopNormalize => values
  }
}

object Normalize {
  given normalizeDecoder: Decoder[Normalize] = Decoder.decodeString.emapTry {
    case "noop"     => Success(NoopNormalize)
    case "linear"   => Success(MinMaxNormalize)
    case "position" => Success(PositionNormalize)
    case other      => Failure(new Exception(s"normalizer $other is not supported"))
  }
}
