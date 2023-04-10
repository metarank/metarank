package ai.metarank.ml.onnx.encoder

import ai.metarank.ml.onnx.EmbeddingCache

case class CachedEncoder(keyCache: EmbeddingCache, valueCache: EmbeddingCache, child: Encoder) extends Encoder {
  override def dim: Int                                               = child.dim
  override def encode(str: String): Option[Array[Float]]              = valueCache.get(str).orElse(child.encode(str))
  override def encode(key: String, str: String): Option[Array[Float]] = keyCache.get(key).orElse(child.encode(str))
}
