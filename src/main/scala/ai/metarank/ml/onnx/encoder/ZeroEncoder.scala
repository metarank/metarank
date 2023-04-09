package ai.metarank.ml.onnx.encoder

case class ZeroEncoder(dim: Int) extends Encoder {
  override def encode(str: String): Option[Array[Float]]              = None
  override def encode(key: String, str: String): Option[Array[Float]] = None
}
