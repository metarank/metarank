package ai.metarank.model

import ai.metarank.model.Dimension.{SingleDim, VectorDim}
import ai.metarank.model.Key.FeatureName
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import cats.implicits._

import java.util
import scala.util.hashing.{Hashing, MurmurHash3}

sealed trait MValue {
  def name: FeatureName
  def dim: Dimension
}

object MValue {
  def apply(name: String, value: Double)         = new SingleValue(FeatureName(name), value)
  def apply(name: String, values: Array[Double]) = new VectorValue(FeatureName(name), values, VectorDim(values.length))
  def apply(name: String, value: String, index: Int) = new CategoryValue(FeatureName(name), value, index)

  case class SingleValue(name: FeatureName, value: Double) extends MValue {
    override val dim = SingleDim

    override def equals(obj: Any): Boolean = obj match {
      case SingleValue(xname, xval) =>
        if (xval.isNaN && value.isNaN) {
          xname == name
        } else {
          (xname == name) && (xval == value)
        }
      case _ => false
    }

    override def hashCode(): Int = name.value.hashCode ^ value.hashCode()
  }

  object SingleValue {
    def missing(name: FeatureName) = new SingleValue(name, Double.NaN)
  }

  case class VectorValue(name: FeatureName, values: Array[Double], dim: VectorDim) extends MValue {
    // so we can chech for equality in tests without array upcasting tricks
    override def equals(obj: Any): Boolean = obj match {
      case VectorValue(xname, xvalues, xdim) =>
        name.equals(xname) && (util.Arrays.compare(values, xvalues) == 0) && (xdim == dim)
      case _ => false
    }

    override def hashCode(): Int = {
      name.value.hashCode ^ util.Arrays.hashCode(values) ^ dim.dim.hashCode()
    }
  }
  object VectorValue {
    def apply(name: FeatureName, values: Array[Double], dim: Int) = new VectorValue(name, values, VectorDim(dim))

    def missing(name: FeatureName, dim: Int): VectorValue = {
      val nans = new Array[Double](dim)
      util.Arrays.fill(nans, Double.NaN)
      VectorValue(name, nans, VectorDim(dim))
    }
    def missing(name: FeatureName, dim: VectorDim): VectorValue = missing(name, dim.dim)
  }

  case class CategoryValue(name: FeatureName, cat: String, index: Int) extends MValue {
    override val dim = SingleDim

    override def hashCode(): Int = name.value.hashCode ^ cat.hashCode ^ index.hashCode()
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
            val result = value.fold[Either[DecodingFailure, MValue]](
              jsonNull = Right(SingleValue.missing(FeatureName(name))),
              jsonBoolean = _ => Left(DecodingFailure(s"cannot decode bool MValue '$value'", c.history)),
              jsonNumber = num => Right(SingleValue(FeatureName(name), num.toDouble)),
              jsonString = str =>
                str.split('@').toList match {
                  case cat :: index :: Nil => Right(CategoryValue(FeatureName(name), cat, index.toInt))
                  case _                   => Left(DecodingFailure(s"cannot decode mvalue $value", c.history))
                },
              jsonObject = _ => Left(DecodingFailure(s"cannot decode object MValue '$value'", c.history)),
              jsonArray = array => {
                val numbers = array.foldLeft[Either[DecodingFailure, List[Double]]](Right(List.empty[Double])) {
                  case (Right(acc), json) =>
                    json.fold(
                      jsonNull = Right(Double.NaN +: acc),
                      jsonNumber = num => Right(num.toDouble +: acc),
                      jsonBoolean = _ => Left(DecodingFailure(s"cannot parse number $json", c.history)),
                      jsonString = _ => Left(DecodingFailure(s"cannot parse number $json", c.history)),
                      jsonArray = _ => Left(DecodingFailure(s"cannot parse number $json", c.history)),
                      jsonObject = _ => Left(DecodingFailure(s"cannot parse number $json", c.history))
                    )
                  case (Left(err), _) => Left(err)
                }
                numbers.map(n => {
                  val array = n.toArray
                  VectorValue(FeatureName(name), array, array.length)
                })
              }
            )
            result
          }
          .toList
          .sequence
      case None => Left(DecodingFailure("oops", c.history))
    }
  )

}
