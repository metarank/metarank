package ai.metarank.model

import io.circe.*

sealed trait FeatureWeight

object FeatureWeight {
  case class SingularWeight(value: Double) extends FeatureWeight

  case class VectorWeight(values: Array[Double]) extends FeatureWeight

  given featureWeightEncoder: Encoder[FeatureWeight] = Encoder.instance {
    case SingularWeight(value) => Json.fromDoubleOrNull(value)
    case VectorWeight(values)  => Json.fromValues(values.map(Json.fromDoubleOrNull))
  }
  given featureWeightDecoder: Decoder[FeatureWeight] = Decoder.instance(c =>
    c.focus match {
      case Some(value) =>
        value.fold(
          jsonNull = Left(DecodingFailure("cannot decode null", c.history)),
          jsonBoolean = _ => Left(DecodingFailure(s"expected weight, got $value", c.history)),
          jsonNumber = n => Right(SingularWeight(n.toDouble)),
          jsonString = _ => Left(DecodingFailure(s"expected weight, got $value", c.history)),
          jsonObject = _ => Left(DecodingFailure(s"expected weight, got $value", c.history)),
          jsonArray = arr => Right(VectorWeight(arr.flatMap(_.asNumber.map(_.toDouble)).toArray))
        )
      case None => Left(DecodingFailure("empty json", c.history))
    }
  )
  given featureWeightCodec: Codec[FeatureWeight] = Codec.from(featureWeightDecoder, featureWeightEncoder)
}
