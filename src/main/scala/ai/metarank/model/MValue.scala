package ai.metarank.model

import ai.metarank.model.Key.FeatureName
import io.circe.{Codec, Decoder, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.deriveCodec

sealed trait MValue {
  def dim: Int
}

object MValue {
  case class SingleValue(name: String, value: Double) extends MValue {
    override val dim: Int = 1
  }

  object SingleValue {
    def apply(name: FeatureName, value: Double) = new SingleValue(name.value, value)
  }

  case class VectorValue(names: List[String], values: Array[Double], dim: Int) extends MValue
  case class CategoryValue(name: String, index: Int) extends MValue {
    override val dim: Int = 1
  }

  object VectorValue {
    def empty(names: List[String], dim: Int) = VectorValue(names, new Array[Double](dim), dim)
  }

  implicit val singleCodec: Codec[SingleValue] = deriveCodec
  implicit val catCodec: Codec[CategoryValue]  = deriveCodec
  implicit val vectorEncoder: Encoder[VectorValue] =
    Encoder.instance(vec =>
      Json.fromJsonObject(
        JsonObject.fromMap(
          Map(
            "names"  -> Json.fromValues(vec.names.map(Json.fromString)),
            "values" -> Json.fromValues(vec.values.map(Json.fromDoubleOrNull))
          )
        )
      )
    )

  implicit val vectorDecoder: Decoder[VectorValue] = Decoder.instance(c =>
    for {
      names  <- c.downField("names").as[List[String]]
      values <- c.downField("values").as[Array[Double]]
    } yield {
      VectorValue(names, values, values.length)
    }
  )

  implicit val mvalueEncoder: Encoder[MValue] = Encoder.instance {
    case s: SingleValue   => singleCodec(s)
    case c: CategoryValue => catCodec(c)
    case v: VectorValue   => vectorEncoder(v)
  }

  implicit val mvalueDecoder: Decoder[MValue] = Decoder.instance(c => {
    if (c.downField("names").focus.isDefined) {
      vectorDecoder(c)
    } else {
      if (c.downField("index").focus.isDefined) {
        catCodec(c)
      } else {
        singleCodec(c)
      }

    }
  })
}
