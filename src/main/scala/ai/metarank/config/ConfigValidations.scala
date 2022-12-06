package ai.metarank.config

import ai.metarank.config.ModelConfig.LambdaMARTConfig

object ConfigValidations {
  def checkFeatureModelReferences(config: Config): List[String] = {
    config.models.toList.flatMap {
      case (name, LambdaMARTConfig(_, features, _, _)) =>
        features.toList.flatMap(feature =>
          if (config.features.exists(_.name == feature)) None
          else Some(s"feature ${feature.value} referenced in model '$name', but missing in features section")
        )
      case _ => Nil
    }
  }

}
