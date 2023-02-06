package ai.metarank.main.command.autofeature.model

import ai.metarank.main.command.autofeature.model.ModelGenerator.ModelConfigMirror
import ai.metarank.ml.recommend.TrendingRecommender.{InteractionWeight, TrendingConfig}

object SimilarRecsConfigGenerator extends RecsConfigGenerator {
  override def name: String = "similar"

  override def makeConfig(ints: List[String]): ModelConfigMirror = ModelConfigMirror(
    name = name,
    conf = TrendingConfig(
      weights = ints.map(it => InteractionWeight(it))
    )
  )
}
