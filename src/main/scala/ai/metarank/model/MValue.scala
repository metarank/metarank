package ai.metarank.model

import ai.metarank.model.Key.FeatureName
import io.circe.{Codec, Decoder, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.deriveCodec

import java.util

sealed trait MValue {
  def name: FeatureName
  def dim: Int
}

object MValue {
  case class SingleValue(name: FeatureName, value: Double) extends MValue {
    override val dim: Int = 1
  }

  case class VectorValue(name: FeatureName, values: Array[Double], dim: Int) extends MValue {
    // so we can chech for equality in tests without array upcasting tricks
    override def equals(obj: Any): Boolean = obj match {
      case VectorValue(xname, xvalues, xdim) =>
        name.equals(xname) && (util.Arrays.compare(values, xvalues) == 0) && (xdim == dim)
      case _ => false
    }
  }
  case class CategoryValue(name: FeatureName, index: Int) extends MValue {
    override val dim: Int = 1
  }

  object VectorValue {
    def empty(name: FeatureName, dim: Int) = VectorValue(name, new Array[Double](dim), dim)
  }

  implicit val singleCodec: Codec[SingleValue] = deriveCodec
  implicit val catCodec: Codec[CategoryValue]  = deriveCodec
  implicit val vectorEncoder: Encoder[VectorValue] =
    Encoder.instance(vec =>
      Json.fromJsonObject(
        JsonObject.fromMap(
          Map(
            "name"   -> Json.fromString(vec.name.value),
            "values" -> Json.fromValues(vec.values.map(Json.fromDoubleOrNull))
          )
        )
      )
    )

  implicit val vectorDecoder: Decoder[VectorValue] = Decoder.instance(c =>
    for {
      name   <- c.downField("name").as[String]
      values <- c.downField("values").as[Array[Double]]
    } yield {
      VectorValue(FeatureName(name), values, values.length)
    }
  )

  implicit val mvalueEncoder: Encoder[MValue] = Encoder.instance {
    case s: SingleValue   => singleCodec(s)
    case c: CategoryValue => catCodec(c)
    case v: VectorValue   => vectorEncoder(v)
  }

  implicit val mvalueDecoder: Decoder[MValue] = Decoder.instance(c => {
    if (c.downField("values").focus.isDefined) {
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
