package ai.metarank.model

import ai.metarank.model.Key.FeatureName
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.deriveCodec
import cats.implicits._
import java.util

sealed trait MValue {
  def name: FeatureName
  def dim: Int
}

object MValue {
  def apply(name: String, value: Double) = new SingleValue(FeatureName(name), value)
  def apply(name: String, values: Array[Double]) = new VectorValue(FeatureName(name), values, values.length)
  def apply(name: String, value: String, index: Int) = new CategoryValue(FeatureName(name), value, index)

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
  object VectorValue {
    def empty(name: FeatureName, dim: Int)         = VectorValue(name, new Array[Double](dim), dim)
  }

  case class CategoryValue(name: FeatureName, cat: String, index: Int) extends MValue {
    override val dim: Int = 1
  }


  implicit val mvalueListEncoder: Encoder[List[MValue]] = Encoder.instance(values =>
    Json.fromJsonObject(JsonObject.fromMap(values.map {
      case SingleValue(name, value)        => name.value -> Json.fromDoubleOrNull(value)
      case VectorValue(name, values, dim)  => name.value -> Json.fromValues(values.map(Json.fromDoubleOrNull))
      case CategoryValue(name, cat, index) => name.value -> Json.fromString(cat + "@" + index.toString)
    }.toMap))
  )

  implicit val mvalueListDecoder: Decoder[List[MValue]] = Decoder.instance(c =>
    c.value.asObject match {
      case Some(obj) =>
        obj.toMap
          .map { case (name, value) =>
            value.asNumber match {
              case Some(num) => Right(SingleValue(FeatureName(name), num.toDouble))
              case None =>
                value.asArray match {
                  case Some(array) if array.forall(_.isNumber) =>
                    val nums = array.flatMap(_.asNumber.map(_.toDouble)).toArray
                    Right(VectorValue(FeatureName(name), nums, nums.length))
                  case _ =>
                    value.asString match {
                      case Some(str) =>
                        str.split('@').toList match {
                          case cat :: index :: Nil => Right(CategoryValue(FeatureName(name), cat, index.toInt))
                          case _                   => Left(DecodingFailure(s"cannot decode mvalue $value", c.history))
                        }
                      case _ => Left(DecodingFailure(s"cannot decode mvalue $value", c.history))
                    }

                }
            }
          }
          .toList
          .sequence
      case None => Left(DecodingFailure("oops", c.history))
    }
  )

}
