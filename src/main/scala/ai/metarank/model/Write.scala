package ai.metarank.model

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.*

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

  given putCodec: Codec[Put]                       = deriveCodec[Put]
  given putTupleCodec: Codec[PutTuple]             = deriveCodec[PutTuple]
  given incCodec: Codec[Increment]                 = deriveCodec[Increment]
  given periodicIncCodec: Codec[PeriodicIncrement] = deriveCodec[PeriodicIncrement]
  given appendCodec: Codec[Append]                 = deriveCodec[Append]
  given putStatCodec: Codec[PutStatSample]         = deriveCodec[PutStatSample]
  given putFreqCodec: Codec[PutFreqSample]         = deriveCodec[PutFreqSample]

  given writeEncoder: Encoder[Write] = Encoder.instance {
    case w: Put               => putCodec(w).deepMerge(typeField("put"))
    case w: PutTuple          => putTupleCodec(w).deepMerge(typeField("put-tuple"))
    case w: Increment         => incCodec(w).deepMerge(typeField("inc"))
    case w: PeriodicIncrement => periodicIncCodec(w).deepMerge(typeField("per-inc"))
    case w: Append            => appendCodec(w).deepMerge(typeField("append"))
    case w: PutStatSample     => putStatCodec(w).deepMerge(typeField("put-stat"))
    case w: PutFreqSample     => putFreqCodec(w).deepMerge(typeField("put-freq"))
  }

  given writeDecoder: Decoder[Write] = Decoder.instance(c =>
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

  given writeCodec: Codec[Write] = Codec.from(writeDecoder, writeEncoder)

  def typeField(tpe: String) = Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString(tpe))))
}
