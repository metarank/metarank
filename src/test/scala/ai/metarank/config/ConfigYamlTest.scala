package ai.metarank.config

import ai.metarank.model.FeatureSchema.NumberFeatureSchema
import ai.metarank.model.FeatureSource.Metadata
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfigYamlTest extends AnyFlatSpec with Matchers {
  it should "parse example config" in {
    val yaml =
      """feature:
        |  - name: price
        |    type: number
        |    field: price
        |    source: metadata""".stripMargin
    val conf = parse(yaml).flatMap(_.as[Config])
    conf shouldBe Right(
      Config(
        feature = List(NumberFeatureSchema("price", "price", Metadata))
      )
    )
  }
}
