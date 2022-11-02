package ai.metarank.fstore.codec.values

class StringVCodecTest extends VCodecTest[String] {
  override val codec    = StringVCodec
  override val instance = "yolo"
}
