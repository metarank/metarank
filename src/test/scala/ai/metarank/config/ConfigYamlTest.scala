package ai.metarank.config

import ai.metarank.config.Config.{InteractionConfig, ModelConfig}
import ai.metarank.config.Config.ModelConfig.{LambdaMART, Shuffle, XGBoostBackend}
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
        |interactions:
        |  - name: click
        |    weight: 1
        |models:
        |  test:
        |    type: lambdamart
        |    backend: xgboost
        |    features: [ price ]""".stripMargin
    val conf = parse(yaml).flatMap(_.as[Config])
    conf shouldBe Right(
      Config(
        features = NonEmptyList.of(NumberFeatureSchema("price", FieldName(Item, "price"), ItemScope)),
        interactions = NonEmptyList.of(InteractionConfig("click", 1)),
        models = Map("test" -> LambdaMART(XGBoostBackend, NonEmptyList.of("price")))
      )
    )
  }

}
