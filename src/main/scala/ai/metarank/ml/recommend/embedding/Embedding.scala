package ai.metarank.ml.recommend.embedding

import com.github.jelmerk.knn.Item

case class Embedding(id: String, vector: Array[Double]) extends Item[String, Array[Double]] {
  override val dimensions = vector.length
}

