package ai.metarank.fstore.codec

import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.fstore.codec.impl.{
  BinaryCodec,
  ClickthroughValuesCodec,
  FeatureValueCodec,
  ScalarCodec,
  TimeValueCodec
}
import ai.metarank.fstore.codec.values.{BinaryVCodec, JsonVCodec}
import ai.metarank.ml.Model
import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{ClickthroughValues, EventId, FeatureValue, Key, Scalar, Scope}
import ai.metarank.util.DelimitedPair.SlashDelimitedPair
import io.circe.{Codec, Decoder, Encoder, Json}
import org.apache.commons.codec.binary.Base64

import java.io.{DataInput, DataOutput}
import scala.util.{Failure, Success}

sealed trait StoreFormat {
  def key: KCodec[Key]
  def timeValue: VCodec[TimeValue]
  def eventId: KCodec[EventId]
  def ctv: VCodec[ClickthroughValues]
  def scalar: VCodec[Scalar]
  def modelName: KCodec[ModelName]
  def model: VCodec[Array[Byte]]
  def featureValue: VCodec[FeatureValue]
}

object StoreFormat {
  case object JsonStoreFormat extends StoreFormat {
    lazy val key          = keyEncoder
    lazy val timeValue    = JsonVCodec[TimeValue](FeatureValue.timeValueCodec)
    lazy val eventId      = idEncoder
    lazy val ctv          = JsonVCodec[ClickthroughValues](ClickthroughValues.ctvJsonCodec)
    lazy val scalar       = JsonVCodec[Scalar](Scalar.scalarJsonCodec)
    lazy val modelName    = KCodec.wrap[ModelName](ModelName.apply, _.name)
    lazy val model        = JsonVCodec[Array[Byte]](byteArrayCodec)
    lazy val featureValue = JsonVCodec[FeatureValue](FeatureValue.featureValueCodec)
  }

  case object BinaryStoreFormat extends StoreFormat {
    lazy val key          = keyEncoder
    lazy val timeValue    = BinaryVCodec(compress = false, TimeValueCodec)
    lazy val eventId      = idEncoder
    lazy val ctv          = BinaryVCodec(compress = true, ClickthroughValuesCodec)
    lazy val scalar       = BinaryVCodec(compress = false, ScalarCodec)
    lazy val modelName    = KCodec.wrap[ModelName](ModelName.apply, _.name)
    lazy val model        = BinaryVCodec(compress = false, BinaryCodec.byteArray)
    lazy val featureValue = BinaryVCodec(compress = false, FeatureValueCodec)
  }

  val keyEncoder: KCodec[Key] = new KCodec[Key] {
    override def encode(prefix: String, value: Key): String = s"$prefix/${value.feature.value}/${value.scope.asString}"

    override def decodeNoPrefix(str: String): Either[Throwable, Key] = str.split('/').toList match {
      case value :: scope :: Nil => Scope.fromString(scope).map(s => Key(s, FeatureName(value)))
      case other                 => Left(new Exception(s"cannot parse key $other"))
    }

    override def encodeNoPrefix(value: Key): String = s"${value.feature.value}/${value.scope.asString}"

    override def decode(str: String): Either[Throwable, Key] = {
      str.split('/').toList match {
        case _ :: value :: scope :: Nil => Scope.fromString(scope).map(s => Key(s, FeatureName(value)))
        case other                      => Left(new Exception(s"cannot parse key $other"))
      }
    }
  }

  val idEncoder: KCodec[EventId] = new KCodec[EventId] {
    override def encode(prefix: String, value: EventId): String = s"$prefix/${value.value}"

    override def decodeNoPrefix(str: String): Either[Throwable, EventId] = Right(EventId(str))

    override def encodeNoPrefix(value: EventId): String = value.value
    override def decode(str: String): Either[Throwable, EventId] = str match {
      case SlashDelimitedPair(_, id) => Right(EventId(id))
      case other                     => Left(new Exception(s"cannot parse id $other"))
    }
  }

  implicit val formatCodec: Codec[StoreFormat] = Codec.from[StoreFormat](
    decodeA = Decoder.decodeString.emapTry {
      case "json"   => Success(JsonStoreFormat)
      case "binary" => Success(BinaryStoreFormat)
      case other    => Failure(new Exception(s"cannot decode format $other"))
    },
    encodeA = Encoder.instance {
      case JsonStoreFormat   => Json.fromString("json")
      case BinaryStoreFormat => Json.fromString("binary")
    }
  )

  implicit val byteArrayCodec: Codec[Array[Byte]] = Codec.from[Array[Byte]](
    decodeA = Decoder.decodeString.map(str => Base64.decodeBase64(str)),
    encodeA = Encoder.encodeString.contramap(b => Base64.encodeBase64String(b))
  )
}
