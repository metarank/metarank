package ai.metarank.fstore.codec.values

import io.circe.{Codec, Decoder, Encoder}

class JsonVCodecTest extends VCodecTest[String] {
  override val codec    = JsonVCodec(Codec.from(Decoder.decodeString, Encoder.encodeString))
  override val instance = "yolo"
}
