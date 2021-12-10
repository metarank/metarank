package ai.metarank.mode.inference.ranking

import io.github.metarank.ltrlib.model.Query

trait RankScorer {
  def score(input: Query): Array[Double]
}
