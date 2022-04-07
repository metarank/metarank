package ai.metarank.config

import ai.metarank.config.Config.InteractionConfig
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType._
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
        |    weight: 1""".stripMargin
    val conf = parse(yaml).flatMap(_.as[Config])
    conf shouldBe Right(
      Config(
        features = List(NumberFeatureSchema("price", FieldName(Item, "price"), ItemScope)),
        interactions = List(InteractionConfig("click", 1))
      )
    )
  }
}
