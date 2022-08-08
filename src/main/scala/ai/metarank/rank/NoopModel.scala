package ai.metarank.rank

import ai.metarank.config.ModelConfig.NoopConfig
import ai.metarank.model.ItemValue
import ai.metarank.model.{Clickthrough, Event, FeatureValue}
import ai.metarank.rank.Model.Scorer
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor, Query}

case class NoopModel(conf: NoopConfig) extends Model {
  override val features                                                  = Nil
  override def datasetDescriptor: DatasetDescriptor                      = DatasetDescriptor(Map.empty, Nil, 0)
  override def train(train: Dataset, test: Dataset): Option[Array[Byte]] = None
}

object NoopModel {

  case object NoopScorer extends Scorer {
    override def score(input: Query): Array[Double] = new Array[Double](input.rows)
  }
}
