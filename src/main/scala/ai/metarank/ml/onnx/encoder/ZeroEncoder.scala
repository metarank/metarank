package ai.metarank.ml.onnx.encoder

case class ZeroEncoder(dim: Int) extends Encoder {
  override def encode(str: String): Array[Float]              = new Array[Float](dim)
  override def encode(key: String, str: String): Array[Float] = new Array[Float](dim)
}
