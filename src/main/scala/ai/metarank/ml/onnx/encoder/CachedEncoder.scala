package ai.metarank.ml.onnx.encoder

import ai.metarank.ml.onnx.EmbeddingCache

case class CachedEncoder(keyCache: EmbeddingCache, valueCache: EmbeddingCache, child: Encoder) extends Encoder {
  override def dim: Int                          = child.dim
  override def encode(str: String): Array[Float] = valueCache.get(str).getOrElse(child.encode(str))
  override def encode(key: String, str: String): Array[Float] = keyCache.get(key).getOrElse(child.encode(str))
}
