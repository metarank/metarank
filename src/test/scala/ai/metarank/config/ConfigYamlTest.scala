package ai.metarank.config

import ai.metarank.config.Config.ModelConfig.{LambdaMARTConfig, ShuffleConfig, XGBoostBackend}
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType._
import cats.data.{NonEmptyList, NonEmptyMap}
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfigYamlTest extends AnyFlatSpec with Matchers {
  it should "parse example config" in {
    val yaml =
      """features:
        |  - name: price
        |    type: number
        |    scope: item
        |    source: metadata.price
        |models:
        |  test:
        |    type: lambdamart
        |    backend: xgboost
        |    features: [ price ]
        |    weights:
        |      click: 1""".stripMargin
    val conf = parse(yaml).flatMap(_.as[Config])
    conf shouldBe Right(
      Config(
        features = NonEmptyList.of(NumberFeatureSchema("price", FieldName(Item, "price"), ItemScope)),
        models = NonEmptyMap.of(
          "test" -> LambdaMARTConfig(XGBoostBackend, NonEmptyList.of("price"), NonEmptyMap.of("click" -> 1))
        )
      )
    )
  }

}
