package ai.metarank.config

import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.StringFeature.EncoderName.{IndexEncoderName, OnehotEncoderName}
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.model.Event.InteractionEvent
import ai.metarank.model.{FeatureSchema, FieldName}
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.FieldName.EventType._
import ai.metarank.model.Key.FeatureName
import cats.data.NonEmptyList
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class FeatureSchemaTest extends AnyFlatSpec with Matchers {
  it should "decode config for number" in {
    decodeYaml("name: price\ntype: number\nscope: item\nsource: metadata.price") shouldBe Right(
      NumberFeatureSchema(FeatureName("price"), FieldName(Item, "price"), ItemScopeType)
    )
  }

  it should "decode config for number with refresh" in {
    decodeYaml("name: price\ntype: number\nscope: item\nsource: metadata.price\nrefresh: 1m") shouldBe Right(
      NumberFeatureSchema(FeatureName("price"), FieldName(Item, "price"), ItemScopeType, Some(1.minute))
    )
  }

  it should "decode config for string" in {
    decodeYaml("name: price\ntype: string\nscope: item\nsource: metadata.price\nvalues: [\"foo\"]") shouldBe Right(
      StringFeatureSchema(
        FeatureName("price"),
        FieldName(Item, "price"),
        ItemScopeType,
        Some(IndexEncoderName),
        NonEmptyList.one("foo")
      )
    )
  }

  it should "decode config for string with custom encoder" in {
    decodeYaml(
      "name: price\ntype: string\nscope: item\nencode: onehot\nsource: metadata.price\nvalues: [\"foo\"]"
    ) shouldBe Right(
      StringFeatureSchema(
        FeatureName("price"),
        FieldName(Item, "price"),
        ItemScopeType,
        Some(OnehotEncoderName),
        NonEmptyList.one("foo")
      )
    )
  }

  it should "decode config for boolean" in {
    decodeYaml("name: price\ntype: boolean\nscope: item\nsource: metadata.price") shouldBe Right(
      BooleanFeatureSchema(FeatureName("price"), FieldName(Item, "price"), ItemScopeType)
    )
  }

  it should "decode feature source" in {
    decodeYaml("name: price\ntype: boolean\nscope: item\nsource: interaction:click.foo") shouldBe Right(
      BooleanFeatureSchema(FeatureName("price"), FieldName(Interaction("click"), "foo"), ItemScopeType)
    )
  }

  it should "decode broken feature source with normal error message" in {
    decodeYaml("type: boolean\nscope: item\nsource: interaction:click.foo").left.map(_.getMessage) shouldBe Left(
      "cannot parse a feature definition of type 'boolean': DownField(name)"
    )
  }

  def decodeYaml(yaml: String) = parse(yaml).flatMap(_.as[FeatureSchema])
}
