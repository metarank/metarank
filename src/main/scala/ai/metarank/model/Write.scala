package ai.metarank.model

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto._

sealed trait Write {
  def key: Key
  def ts: Timestamp
}
object Write {
  case class Put(key: Key, ts: Timestamp, value: Scalar)                              extends Write
  case class PutTuple(key: Key, ts: Timestamp, mapKey: String, value: Option[Scalar]) extends Write

  case class Increment(key: Key, ts: Timestamp, inc: Int)         extends Write
  case class PeriodicIncrement(key: Key, ts: Timestamp, inc: Int) extends Write

  case class Append(key: Key, value: Scalar, ts: Timestamp) extends Write

  case class PutStatSample(key: Key, ts: Timestamp, value: Double) extends Write
  case class PutFreqSample(key: Key, ts: Timestamp, value: String) extends Write

  implicit val putCodec: Codec[Put]                       = deriveCodec[Put]
  implicit val putTupleCodec: Codec[PutTuple]             = deriveCodec[PutTuple]
  implicit val incCodec: Codec[Increment]                 = deriveCodec[Increment]
  implicit val periodicIncCodec: Codec[PeriodicIncrement] = deriveCodec[PeriodicIncrement]
  implicit val appendCodec: Codec[Append]                 = deriveCodec[Append]
  implicit val putStatCodec: Codec[PutStatSample]         = deriveCodec[PutStatSample]
  implicit val putFreqCodec: Codec[PutFreqSample]         = deriveCodec[PutFreqSample]

  implicit val writeEncoder: Encoder[Write] = Encoder.instance {
    case w: Put               => putCodec(w).deepMerge(typeField("put"))
    case w: PutTuple          => putTupleCodec(w).deepMerge(typeField("put-tuple"))
    case w: Increment         => incCodec(w).deepMerge(typeField("inc"))
    case w: PeriodicIncrement => periodicIncCodec(w).deepMerge(typeField("per-inc"))
    case w: Append            => appendCodec(w).deepMerge(typeField("append"))
    case w: PutStatSample     => putStatCodec(w).deepMerge(typeField("put-stat"))
    case w: PutFreqSample     => putFreqCodec(w).deepMerge(typeField("put-freq"))
  }

  implicit val writeDecoder: Decoder[Write] = Decoder.instance(c =>
    for {
      tpe <- c.downField("type").as[String]
      write <- tpe match {
        case "put"       => putCodec.tryDecode(c)
        case "put-tuple" => putTupleCodec.tryDecode(c)
        case "inc"       => incCodec.tryDecode(c)
        case "per-inc"   => periodicIncCodec.tryDecode(c)
        case "append"    => appendCodec.tryDecode(c)
        case "put-stat"  => putStatCodec.tryDecode(c)
        case "put-freq"  => putFreqCodec.tryDecode(c)
        case other       => Left(DecodingFailure(s"write type $other not supported", c.history))
      }
    } yield {
      write
    }
  )

  implicit val writeCodec: Codec[Write] = Codec.from(writeDecoder, writeEncoder)

  def typeField(tpe: String) = Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString(tpe))))
}
