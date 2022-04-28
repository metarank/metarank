package ai.metarank.util

import ai.metarank.rank.Model.Scorer
import io.github.metarank.ltrlib.model.Query

import scala.util.Random

case class RandomScorer(seed: Int = 0) extends Scorer {
  val random = new Random(seed)

  override def score(input: Query): Array[Double] = {
    Array.fill(input.rows)(random.nextDouble())
  }
}
