package ai.metarank.rank

import ai.metarank.config.ModelConfig.NoopConfig
import ai.metarank.model.{Clickthrough, Event, FeatureValue, ItemValue, TrainResult}
import ai.metarank.rank.Model.{Scorer, TrainedModel}
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor, Query}

import java.util

case class NoopModel(conf: NoopConfig) extends Model {
  override val features                             = Nil
  override def datasetDescriptor: DatasetDescriptor = DatasetDescriptor(Map.empty, Nil, 0)
  override def train(train: Dataset, test: Dataset) = TrainedModel(Array.emptyByteArray, Map.empty)
}

object NoopModel {

  case object NoopScorer extends Scorer {
    override def score(input: Query): Array[Double] = new Array[Double](input.rows)
  }

}
