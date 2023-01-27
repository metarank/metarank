package ai.metarank.model

import ai.metarank.config.BoosterConfig.{LightGBMConfig, XGBoostConfig}

import java.io.OutputStream

sealed trait BoosterConfigFile {
  def write(stream: OutputStream): Unit
}

object BoosterConfigFile {
  case class XGBoostConfigFile(conf: XGBoostConfig, train: String, test: String) extends BoosterConfigFile {
    override def write(stream: OutputStream): Unit = {
      val out = s"""eta=${conf.learningRate}
                   |max_depth=${conf.maxDepth}
                   |subsample=${conf.sampling}
                   |num_round=${conf.iterations}
                   |objective=rank:pairwise
                   |eval_metric=ndcg@${conf.ndcgCutoff}
                   |seed=${conf.seed}
                   |data=train.svm
                   |test:data=test.svm
                   |eval[train=train.svm
                   |eval[test]=test.svm""".stripMargin
      stream.write(out.getBytes())
    }
  }

  case class LightGBMConfigFile(conf: LightGBMConfig, train: String, test: String, cats: List[String])
      extends BoosterConfigFile {
    override def write(stream: OutputStream): Unit = {
      val catRow = cats match {
        case Nil  => ""
        case list => s"categorial_feature: ${list.map(f => s"name:$f").mkString(",")}"
      }
      val out = s"""objective=lambdarank
                   |data=$train
                   |valid=$test
                   |num_iterations=${conf.iterations}
                   |learning_rate=${conf.learningRate}
                   |seed=${conf.seed}
                   |max_depth=${conf.maxDepth}
                   |header=true
                   |label_column=name:label
                   |group_column=name:group
                   |$catRow
                   |lambdarank_truncation_level=${conf.ndcgCutoff}
                   |metric=ndcg
                   |eval_at=${conf.ndcgCutoff}""".stripMargin
      stream.write(out.getBytes())
    }
  }
}
