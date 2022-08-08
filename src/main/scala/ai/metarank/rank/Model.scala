package ai.metarank.rank

import ai.metarank.config.ModelConfig
import ai.metarank.feature.BaseFeature
import ai.metarank.model.ItemValue
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.model.{FeatureValue, Schema}
import ai.metarank.util.Logging
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor, Query}

trait Model extends Logging {
  def conf: ModelConfig
  def features: List[BaseFeature]
  def datasetDescriptor: DatasetDescriptor
  def train(train: Dataset, test: Dataset): Option[Array[Byte]]
}

object Model {
  trait Scorer {
    def score(input: Query): Array[Double]
  }
}
