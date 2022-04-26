package ai.metarank.model

import ai.metarank.config.Config.ModelConfig
import ai.metarank.feature.BaseFeature
import ai.metarank.model.Clickthrough.ItemValues
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.util.Logging
import io.findify.featury.model.{FeatureValue, Schema}
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor, Query}

trait Ranker extends Logging {
  def conf: ModelConfig
  def features: List[BaseFeature]
  def datasetDescriptor: DatasetDescriptor
  lazy val schema: Schema = Schema(features.flatMap(_.states))
  def featureValues(
      ranking: RankingEvent,
      source: List[FeatureValue],
      interactions: List[InteractionEvent] = Nil
  ): List[ItemValues]

  def train(train: Dataset, test: Dataset, iterations: Int): Option[Array[Byte]]
}
