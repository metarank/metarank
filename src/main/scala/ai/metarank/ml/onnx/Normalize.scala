package ai.metarank.ml.onnx

import ai.metarank.model.MValue.SingleValue
import io.circe.Decoder

import scala.util.{Failure, Success}

sealed trait Normalize {
  def scale(values: List[SingleValue]): List[SingleValue]
}

object Normalize {
  case object MinMaxNormalize extends Normalize {
    override def scale(values: List[SingleValue]): List[SingleValue] = {
      val scores = values.map(_.value).filterNot(_.isNaN)
      (scores.minOption, scores.maxOption) match {
        case (Some(min), Some(max)) =>
          values.map(v => v.copy(value = (v.value - min) / (max - min)))
        case _ => values
      }
    }
  }

  case object PositionNormalize extends Normalize {
    override def scale(values: List[SingleValue]): List[SingleValue] = {
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
    }
  }

  case object NoopNormalize extends Normalize {
    override def scale(values: List[SingleValue]): List[SingleValue] = values
  }

  implicit val normalizeDecoder: Decoder[Normalize] = Decoder.decodeString.emapTry {
    case "noop"     => Success(NoopNormalize)
    case "linear"   => Success(MinMaxNormalize)
    case "position" => Success(PositionNormalize)
    case other      => Failure(new Exception(s"normalizer $other is not supported"))
  }
}
