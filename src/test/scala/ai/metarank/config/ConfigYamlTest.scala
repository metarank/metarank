package ai.metarank.config

import ai.metarank.model.FeatureSchema.NumberFeatureSchema
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.Metadata
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfigYamlTest extends AnyFlatSpec with Matchers {
  it should "parse example config" in {
    val yaml =
      """feature:
        |  - name: price
        |    type: number
        |    scope: item
        |    source: metadata.price""".stripMargin
    val conf = parse(yaml).flatMap(_.as[Config])
    conf shouldBe Right(
      Config(
        feature = List(NumberFeatureSchema("price", FieldName(Metadata, "price"), ItemScope))
      )
    )
  }
}
