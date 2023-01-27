package ai.metarank.ml.recommend.mf

import ai.metarank.config.{ModelConfig, Selector}
import ai.metarank.ml.recommend.embedding.EmbeddingMap
import fs2.io.file.Path

trait MFRecImpl {
  def train(file: Path): EmbeddingMap
}

object MFRecImpl {
  trait MFModelConfig extends ModelConfig {
    def interactions: List[String]
    def iterations: Int
    def factors: Int
    def userReg: Float
    def itemReg: Float
    def m: Int
    def ef: Int
    def selector: Selector
  }
}
