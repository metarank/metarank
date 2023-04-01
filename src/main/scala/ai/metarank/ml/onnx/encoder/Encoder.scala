package ai.metarank.ml.onnx.encoder

import ai.metarank.model.Identifier.ItemId
import cats.effect.IO

trait Encoder {
  def encode(str: String): Array[Float]
  def encode(id: ItemId, str: String): Array[Float]
  def dim: Int
}

object Encoder {
  def create(conf: EncoderType): IO[Encoder] = conf match {
    case EncoderType.BertEncoderType(model, modelFile, vocabFile) => BertEncoder.create(model, modelFile, vocabFile)
    case EncoderType.CsvEncoderType(path)   => CsvEncoder.create(path)
  }
}
