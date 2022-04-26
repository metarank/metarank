package ai.metarank.ranker

import ai.metarank.config.Config.ModelConfig.NoopConfig
import ai.metarank.model.Clickthrough.ItemValues
import ai.metarank.model.{Clickthrough, Event, Ranker}
import io.findify.featury.model.{FeatureValue, Schema}
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor}

case class NoopRanker(conf: NoopConfig) extends Ranker {
  override val features                             = Nil
  override def datasetDescriptor: DatasetDescriptor = DatasetDescriptor(Map.empty, Nil, 0)
  override def featureValues(
      ranking: Event.RankingEvent,
      source: List[FeatureValue],
      interactions: List[Event.InteractionEvent]
  ): List[Clickthrough.ItemValues] = {
    ranking.items.map(item => ItemValues(item.id, 0.0, Nil, 0)).toList
  }

  override def train(train: Dataset, test: Dataset, iterations: Int): Option[Array[Byte]] = None
}
