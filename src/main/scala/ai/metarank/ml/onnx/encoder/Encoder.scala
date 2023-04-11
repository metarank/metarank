package ai.metarank.ml.onnx.encoder

import ai.metarank.ml.onnx.EmbeddingCache
import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.Logging
import cats.effect.IO

trait Encoder {
  def encode(key: String, str: String): Option[Array[Float]]
  def encode(str: String): Option[Array[Float]]
  def dim: Int
}

object Encoder extends Logging {
  def create(conf: EncoderType): IO[Encoder] = conf match {
    case EncoderType.BertEncoderType(model, itemCache, rankCache, modelFile, vocabFile, dim) =>
      for {
        fields <- rankCache match {
          case Some(path) => info("Loading ranking embeddings") *> EmbeddingCache.fromCSV(path, ',', dim)
          case None       => IO.pure(EmbeddingCache.empty())
        }
        items <- itemCache match {
          case Some(path) => info("Loading item embeddings") *> EmbeddingCache.fromCSV(path, ',', dim)
          case None       => IO.pure(EmbeddingCache.empty())
        }
        bert <- BertEncoder.create(model, modelFile, vocabFile)
      } yield {
        CachedEncoder(items, fields, bert)
      }

    case EncoderType.CsvEncoderType(itemPath, fieldPath, dim) =>
      for {
        _      <- info("Loading ranking embeddings")
        fields <- EmbeddingCache.fromCSV(fieldPath, ',', dim)
        _      <- info("Loading item embeddings")
        items  <- EmbeddingCache.fromCSV(itemPath, ',', dim)

      } yield {
        CachedEncoder(items, fields, ZeroEncoder(dim))
      }
  }
}
