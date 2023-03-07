package ai.metarank.main.command.autofeature.model

import ai.metarank.main.command.autofeature.EventModel
import ai.metarank.model.FeatureSchema
import ai.metarank.util.Logging
import ai.metarank.ml.recommend.TrendingRecommender.{InteractionWeight, TrendingConfig}
import ai.metarank.main.command.autofeature.model.ModelGenerator.ModelConfigMirror

object TrendingRecsConfigGenerator extends RecsConfigGenerator {
  override def name: String = "trending"

  override def makeConfig(ints: List[String]): ModelConfigMirror = ModelConfigMirror(
    name = name,
    conf = TrendingConfig(
      weights = ints.map(it => InteractionWeight(it))
    )
  )
}
