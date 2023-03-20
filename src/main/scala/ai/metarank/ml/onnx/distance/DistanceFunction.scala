package ai.metarank.ml.onnx.distance

import io.circe.Decoder
import io.netty.channel.socket.DuplexChannelConfig

import scala.util.{Failure, Success}

sealed trait DistanceFunction {
  def dist(query: Array[Float], item: Array[Double]): Double
}

object DistanceFunction {
  case object CosineDistance extends DistanceFunction {
    override def dist(query: Array[Float], item: Array[Double]): Double = {
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
    }
  }

  case object DotDistance extends DistanceFunction {
    override def dist(query: Array[Float], item: Array[Double]): Double = ???
  }

  implicit val distanceFunctionDecoder: Decoder[DistanceFunction] = Decoder.decodeString.emapTry {
    case "cos" | "Cos" | "cosine" | "Cosine" => Success(CosineDistance)
    case "dot"                               => Success(DotDistance)
    case other                               => Failure(new Exception(s"distance '$other' is not supported"))
  }
}
