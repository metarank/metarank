package ai.metarank.fstore.redis.codec

import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.fstore.redis.codec.impl.{
  ClickthroughValuesCodec,
  FeatureValueCodec,
  ScalarCodec,
  ScorerCodec,
  TimeValueCodec
}
import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{ClickthroughValues, EventId, FeatureValue, Key, Scalar, Scope}
import ai.metarank.rank.Model.Scorer
import ai.metarank.util.DelimitedPair.SlashDelimitedPair
import io.circe.{Codec, Decoder, Encoder, Json}

import scala.util.{Failure, Success}

sealed trait StoreFormat {
  def key: KCodec[Key]
  def timeValue: VCodec[TimeValue]
  def eventId: KCodec[EventId]
  def ctv: VCodec[ClickthroughValues]
  def scalar: VCodec[Scalar]
  def model: KCodec[ModelName]
  def scorer: VCodec[Scorer]
  def featureValue: VCodec[FeatureValue]
}

object StoreFormat {
  case object JsonStoreFormat extends StoreFormat {
    lazy val key          = keyEncoder
    lazy val timeValue    = VCodec.json[TimeValue](FeatureValue.timeValueCodec)
    lazy val eventId      = idEncoder
    lazy val ctv          = VCodec.json[ClickthroughValues](ClickthroughValues.ctvJsonCodec)
    lazy val scalar       = VCodec.json[Scalar](Scalar.scalarJsonCodec)
    lazy val model        = KCodec.wrap[ModelName](ModelName.apply, _.name)
    lazy val scorer       = VCodec.json[Scorer]
    lazy val featureValue = VCodec.json[FeatureValue]
  }

  case object BinaryStoreFormat extends StoreFormat {

    lazy val key          = keyEncoder
    lazy val timeValue    = VCodec.binary(TimeValueCodec)
    lazy val eventId      = idEncoder
    lazy val ctv          = VCodec.binary(ClickthroughValuesCodec)
    lazy val scalar       = VCodec.binary(ScalarCodec)
    lazy val model        = KCodec.wrap[ModelName](ModelName.apply, _.name)
    lazy val scorer       = VCodec.binary(ScorerCodec)
    lazy val featureValue = VCodec.binary(FeatureValueCodec)
  }

  val keyEncoder: KCodec[Key] = new KCodec[Key] {
    override def encode(prefix: String, value: Key): String = s"$prefix/${value.scope.asString}/${value.feature.value}"
    override def decode(str: String): Either[Throwable, Key] = {
      str.split('/').toList match {
        case _ :: scope :: value :: Nil => Scope.fromString(scope).map(s => Key(s, FeatureName(value)))
        case other                      => Left(new Exception(s"cannot parse key $other"))
      }
    }
  }

  val idEncoder: KCodec[EventId] = new KCodec[EventId] {
    override def encode(prefix: String, value: EventId): String = s"$prefix/${value.value}"

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
}
