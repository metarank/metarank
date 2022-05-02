package ai.metarank.rank

import ai.metarank.config.ModelConfig.NoopConfig
import ai.metarank.model.Clickthrough.ItemValues
import ai.metarank.model.{Clickthrough, Event}
import ai.metarank.rank.Model.Scorer
import io.findify.featury.model.{FeatureValue, Schema}
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor, Query}

case class NoopModel(conf: NoopConfig) extends Model {
  override val features                             = Nil
  override def datasetDescriptor: DatasetDescriptor = DatasetDescriptor(Map.empty, Nil, 0)
  override def featureValues(
      ranking: Event.RankingEvent,
      source: List[FeatureValue],
      interactions: List[Event.InteractionEvent]
  ): List[Clickthrough.ItemValues] = NoopModel.noop(ranking)

  override def train(train: Dataset, test: Dataset): Option[Array[Byte]] = None
}

object NoopModel {
  def noop(ranking: Event.RankingEvent): List[Clickthrough.ItemValues] =
    ranking.items.map(item => ItemValues(item.id, 0.0, Nil, 0)).toList

  case object NoopScorer extends Scorer {
    override def score(input: Query): Array[Double] = new Array[Double](input.rows)
  }
}
