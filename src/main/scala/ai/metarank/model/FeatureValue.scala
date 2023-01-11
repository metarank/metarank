package ai.metarank.model

import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import ai.metarank.model.FeatureValue.PeriodicCounterValue.PeriodicValue
import io.circe._
import io.circe.generic.semiauto._
import shapeless.Lazy

import java.util

sealed trait FeatureValue {
  def key: Key
  def ts: Timestamp
}

object FeatureValue {
  case class ScalarValue(key: Key, ts: Timestamp, value: Scalar) extends FeatureValue
  case class CounterValue(key: Key, ts: Timestamp, value: Long)  extends FeatureValue
  case class NumStatsValue(key: Key, ts: Timestamp, min: Double, max: Double, quantiles: Map[Int, Double])
      extends FeatureValue
  case class MapValue(key: Key, ts: Timestamp, values: Map[String, Scalar]) extends FeatureValue
  case class PeriodicCounterValue(key: Key, ts: Timestamp, values: Array[PeriodicValue]) extends FeatureValue {
    override def equals(obj: Any): Boolean = obj match {
      case PeriodicCounterValue(xkey, xts, xvalues) =>
        (key == xkey) && (ts == xts) && (util.Arrays.deepEquals(
          values.asInstanceOf[Array[Object]],
          xvalues.asInstanceOf[Array[Object]]
        ))
      case _ => false
    }
  }
  object PeriodicCounterValue {
    case class PeriodicValue(start: Timestamp, end: Timestamp, periods: Int, value: Long)
  }
  case class FrequencyValue(key: Key, ts: Timestamp, values: Map[String, Double]) extends FeatureValue
  case class BoundedListValue(key: Key, ts: Timestamp, values: List[TimeValue])   extends FeatureValue
  object BoundedListValue {
    case class TimeValue(ts: Timestamp, value: Scalar)
  }

  implicit val scalarCodec: Codec[ScalarValue]                        = deriveCodec
  implicit val freqCodec: Codec[FrequencyValue]                       = deriveCodec
  implicit val counterCodec: Codec[CounterValue]                      = deriveCodec
  implicit val numStatsCodec: Codec[NumStatsValue]                    = deriveCodec
  implicit val periodicValueCodec: Codec[PeriodicValue]               = deriveCodec
  implicit val periodicCounterValueCodec: Codec[PeriodicCounterValue] = deriveCodec
  implicit val timeValueCodec: Codec[TimeValue]                       = deriveCodec
  implicit val boundedListCodec: Codec[BoundedListValue]              = deriveCodec
  implicit val mapCodec: Codec[MapValue]                              = deriveCodec

  implicit val featureValueEncoder: Encoder[FeatureValue] = Encoder.instance {
    case v: ScalarValue          => scalarCodec.apply(v).deepMerge(tpe("scalar"))
    case v: CounterValue         => counterCodec.apply(v).deepMerge(tpe("counter"))
    case v: NumStatsValue        => numStatsCodec.apply(v).deepMerge(tpe("stats"))
    case v: MapValue             => mapCodec.apply(v).deepMerge(tpe("map"))
    case v: PeriodicCounterValue => periodicCounterValueCodec.apply(v).deepMerge(tpe("pcounter"))
    case v: FrequencyValue       => freqCodec.apply(v).deepMerge(tpe("freq"))
    case v: BoundedListValue     => boundedListCodec.apply(v).deepMerge(tpe("list"))
  }

  def tpe(value: String) = Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString(value))))

  implicit val featureValueDecoder: Decoder[FeatureValue] = Decoder.instance(c =>
    for {
      tpe <- c.downField("type").as[String]
      decoded <- tpe match {
        case "freq"     => freqCodec.tryDecode(c)
        case "scalar"   => scalarCodec.tryDecode(c)
        case "counter"  => counterCodec.tryDecode(c)
        case "stats"    => numStatsCodec.tryDecode(c)
        case "pcounter" => periodicCounterValueCodec.tryDecode(c)
        case "list"     => boundedListCodec.tryDecode(c)
        case "map"      => mapCodec.tryDecode(c)
        case other      => Left(DecodingFailure(s"value type $other is not supported", c.history))
      }
    } yield {
      decoded
    }
  )

  implicit val featureValueCodec: Codec[FeatureValue] = Codec.from(featureValueDecoder, featureValueEncoder)

}
