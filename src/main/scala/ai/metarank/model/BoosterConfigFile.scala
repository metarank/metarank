package ai.metarank.model

import ai.metarank.config.ModelConfig.ModelBackend.{LightGBMBackend, XGBoostBackend}

sealed trait BoosterConfigFile {
  def generate: String
}

object BoosterConfigFile {
  case class XGBoostConfigFile(conf: XGBoostBackend, train: String, test: String) extends BoosterConfigFile {
    override def generate: String = ""
  }

  case class LightGBMConfigFile(conf: LightGBMBackend, train: String, test: String) extends BoosterConfigFile {
    override def generate: String = ""
  }
}
