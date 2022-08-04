package ai.metarank.rank

import ai.metarank.config.ModelConfig.ShuffleConfig
import ai.metarank.model.{Clickthrough, Event, FeatureValue}
import ai.metarank.rank.Model.Scorer
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor, Query}

import scala.util.Random

case class ShuffleModel(conf: ShuffleConfig) extends Model {
  override val features                             = Nil
  override def datasetDescriptor: DatasetDescriptor = DatasetDescriptor(Map.empty, Nil, 0)
  override def featureValues(
      ranking: Event.RankingEvent,
      source: List[FeatureValue],
      interactions: List[Event.InteractionEvent]
  ): List[Clickthrough.ItemValues] = {
    NoopModel.noop(ranking)
  }

  override def train(train: Dataset, test: Dataset): Option[Array[Byte]] = None
}

object ShuffleModel {
  case class ShuffleScorer(maxPositionChange: Int) extends Scorer {
    override def score(input: Query): Array[Double] = {
      for {
        index <- (0 until input.rows).toArray
      } yield {
        index.toDouble + Random.nextInt(2 * maxPositionChange) - maxPositionChange
      }
    }
  }
}
