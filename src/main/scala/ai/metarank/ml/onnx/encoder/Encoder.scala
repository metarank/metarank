package ai.metarank.ml.onnx.encoder

import ai.metarank.ml.onnx.EmbeddingCache
import ai.metarank.model.Identifier.ItemId
import cats.effect.IO

trait Encoder {
  def encode(key: String, str: String): Option[Array[Float]]
  def encode(str: String): Option[Array[Float]]
  def dim: Int
}

object Encoder {
  def create(conf: EncoderType): IO[Encoder] = conf match {
    case EncoderType.BertEncoderType(model, itemCache, rankCache, modelFile, vocabFile, dim) =>
      for {
        items <- itemCache match {
          case Some(path) => EmbeddingCache.fromCSV(path, ',', dim)
          case None       => IO.pure(EmbeddingCache.empty())
        }
        fields <- rankCache match {
          case Some(path) => EmbeddingCache.fromCSV(path, ',', dim)
          case None       => IO.pure(EmbeddingCache.empty())
        }
        bert <- BertEncoder.create(model, modelFile, vocabFile)
      } yield {
        CachedEncoder(items, fields, bert)
      }

    case EncoderType.CsvEncoderType(itemPath, fieldPath, dim) =>
      for {
        items  <- EmbeddingCache.fromCSV(itemPath, ',', dim)
        fields <- EmbeddingCache.fromCSV(fieldPath, ',', dim)
      } yield {
        CachedEncoder(items, fields, ZeroEncoder(dim))
      }
  }
}
