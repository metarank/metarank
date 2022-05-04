package ai.metarank.config

import ai.metarank.config.ModelConfig.ModelBackend
import ai.metarank.config.ModelConfig.ModelBackend.XGBoostBackend
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ModelConfigTest extends AnyFlatSpec with Matchers {
  it should "parse default options for xgboost" in {
    val yaml =
      """type: xgboost
        |seed: 0""".stripMargin
    val decoded = io.circe.yaml.parser.parse(yaml).flatMap(_.as[ModelBackend])
    decoded shouldBe Right(XGBoostBackend(seed = 0))
  }

  it should "parse options for xgboost" in {
    val yaml =
      """type: xgboost
        |iterations: 200
        |learningRate: 0.2
        |ndcgCutoff: 5
        |maxDepth: 7
        |seed: 0""".stripMargin
    val decoded = io.circe.yaml.parser.parse(yaml).flatMap(_.as[ModelBackend])
    decoded shouldBe Right(XGBoostBackend(seed = 0, iterations = 200, learningRate = 0.2, ndcgCutoff = 5, maxDepth = 7))
  }
}
