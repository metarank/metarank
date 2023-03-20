package ai.metarank.ml.onnx.encoder

import ai.metarank.ml.onnx.SBERT
import ai.metarank.model.Identifier.ItemId
import cats.effect.IO

case class BertEncoder(sbert: SBERT) extends Encoder {
  override def dim: Int = sbert.dim

  override def encode(id: ItemId, str: String): Array[Float] = sbert.embed(str)
}

object BertEncoder {
  def create(model: String): IO[BertEncoder] = IO {
    val sbert = SBERT(
      model = this.getClass.getResourceAsStream(s"/sbert/$model.onnx"),
      dic = this.getClass.getResourceAsStream("/sbert/sentence-transformer/vocab.txt")
    )
    BertEncoder(sbert)
  }
}
