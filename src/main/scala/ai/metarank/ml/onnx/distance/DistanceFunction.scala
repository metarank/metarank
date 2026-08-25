package ai.metarank.ml.onnx.distance

import io.circe.Decoder

import scala.util.{Failure, Success}

enum DistanceFunction {
  case CosineDistance
  case DotDistance

  def dist(query: Array[Float], item: Array[Double]): Double = this match {
    case CosineDistance =>
      var topSum = 0.0
      var aSum   = 0.0
      var bSum   = 0.0
      var i      = 0
      while (i < query.length) {
        topSum += query(i) * item(i)
        aSum += query(i) * query(i)
        bSum += item(i) * item(i)
        i += 1
      }
      topSum / (math.sqrt(aSum) * math.sqrt(bSum))
    case DotDistance =>
      var sum = 0.0
      var i   = 0
      while (i < query.length) {
        sum += query(i) * item(i)
        i += 1
      }
      sum
  }
}

object DistanceFunction {
  given distanceFunctionDecoder: Decoder[DistanceFunction] = Decoder.decodeString.emapTry {
    case "cos" | "Cos" | "cosine" | "Cosine" => Success(CosineDistance)
    case "dot"                               => Success(DotDistance)
    case other                               => Failure(new Exception(s"distance '$other' is not supported"))
  }
}
