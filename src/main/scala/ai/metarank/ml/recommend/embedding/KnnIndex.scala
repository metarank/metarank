package ai.metarank.ml.recommend.embedding

import ai.metarank.ml.Model.ItemScore
import ai.metarank.ml.recommend.KnnConfig
import ai.metarank.ml.recommend.embedding.HnswJavaIndex.{HnswIndexWriter, HnswOptions}
import ai.metarank.ml.recommend.embedding.QdrantIndex.{QdrantIndexWriter, QdrantOptions}
import ai.metarank.model.Identifier.ItemId
import cats.effect.IO

object KnnIndex {
  trait KnnIndexReader {
    def lookup(items: List[ItemId], n: Int): IO[List[ItemScore]]
    def save(): Array[Byte]
  }

  trait KnnIndexWriter[R <: KnnIndexReader, O] {
    def load(bytes: Array[Byte], options: O): IO[R]
    def write(embeddings: EmbeddingMap, options: O): IO[R]
  }

  def write(source: EmbeddingMap, config: KnnConfig): IO[KnnIndexReader] = config match {
    case KnnConfig.HnswConfig(m, ef) => HnswIndexWriter.write(source, HnswOptions(m, ef))
    case KnnConfig.QdrantConfig(endpoint, collection, dim, dist) =>
      QdrantIndexWriter.write(source, QdrantOptions(endpoint, collection, dim, dist))
  }

  def load(bytes: Array[Byte], config: KnnConfig): IO[KnnIndexReader] = config match {
    case KnnConfig.HnswConfig(m, ef) => HnswIndexWriter.load(bytes, HnswOptions(m, ef))
    case KnnConfig.QdrantConfig(endpoint, collection, dim, dist) =>
      QdrantIndexWriter.load(bytes, QdrantOptions(endpoint, collection, dim, dist))
  }
}
