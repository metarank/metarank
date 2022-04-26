package ai.metarank.ranker

import ai.metarank.config.Config.ModelConfig.ShuffleConfig
import ai.metarank.model.Clickthrough.ItemValues
import ai.metarank.model.{Clickthrough, Event, Ranker}
import io.findify.featury.model.{FeatureValue, Schema}
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor}

import scala.util.Random

case class ShuffleRanker(conf: ShuffleConfig) extends Ranker {
  override val features                             = Nil
  override def datasetDescriptor: DatasetDescriptor = DatasetDescriptor(Map.empty, Nil, 0)
  override def featureValues(
      ranking: Event.RankingEvent,
      source: List[FeatureValue],
      interactions: List[Event.InteractionEvent]
  ): List[Clickthrough.ItemValues] = {
    ranking.items.zipWithIndex
      .map { case (item, index) =>
        item -> (index + Random.nextInt(2 * conf.maxPositionChange) - conf.maxPositionChange)
      }
      .sortBy(_._2)
      .map(x => ItemValues(x._1.id, 0.0, Nil, x._2))
      .toList
  }

  override def train(train: Dataset, test: Dataset, iterations: Int): Option[Array[Byte]] = None
}
