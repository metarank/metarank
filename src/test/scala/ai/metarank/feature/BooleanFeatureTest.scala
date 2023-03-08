package ai.metarank.feature

import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.parse

class BooleanFeatureTest extends AnyFlatSpec with Matchers {
  it should "parse config with .field (as in docs)" in {
    val yaml = """name: availability
                 |type: boolean
                 |scope: item
                 |field: item.availability""".stripMargin
    parse(yaml).flatMap(_.as[BooleanFeatureSchema]) shouldBe Right(
      BooleanFeatureSchema(
        name = FeatureName("availability"),
        scope = ItemScopeType,
        field = FieldName(Item, "availability")
      )
    )
  }

  it should "parse config with .source (compat)" in {
    val yaml =
      """name: availability
        |type: boolean
        |scope: item
        |source: item.availability""".stripMargin
    parse(yaml).flatMap(_.as[BooleanFeatureSchema]) shouldBe Right(
      BooleanFeatureSchema(
        name = FeatureName("availability"),
        scope = ItemScopeType,
        field = FieldName(Item, "availability")
      )
    )
  }
}
