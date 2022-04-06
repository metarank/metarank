package ai.metarank.config

import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.model.Event.InteractionEvent
import ai.metarank.model.{FeatureSchema, FieldName}
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.FieldName.{Interaction, Item}
import cats.data.NonEmptyList
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class FeatureSchemaTest extends AnyFlatSpec with Matchers {
  it should "decode config for number" in {
    decodeYaml("name: price\ntype: number\nscope: item\nsource: metadata.price") shouldBe Right(
      NumberFeatureSchema("price", FieldName(Item, "price"), ItemScope)
    )
  }

  it should "decode config for number with refresh" in {
    decodeYaml("name: price\ntype: number\nscope: item\nsource: metadata.price\nrefresh: 1m") shouldBe Right(
      NumberFeatureSchema("price", FieldName(Item, "price"), ItemScope, Some(1.minute))
    )
  }

  it should "decode config for string" in {
    decodeYaml("name: price\ntype: string\nscope: item\nsource: metadata.price\nvalues: [\"foo\"]") shouldBe Right(
      StringFeatureSchema("price", FieldName(Item, "price"), ItemScope, NonEmptyList.one("foo"))
    )
  }

  it should "decode config for boolean" in {
    decodeYaml("name: price\ntype: boolean\nscope: item\nsource: metadata.price") shouldBe Right(
      BooleanFeatureSchema("price", FieldName(Item, "price"), ItemScope)
    )
  }

  it should "decode feature source" in {
    decodeYaml("name: price\ntype: boolean\nscope: item\nsource: interaction:click.foo") shouldBe Right(
      BooleanFeatureSchema("price", FieldName(Interaction("click"), "foo"), ItemScope)
    )
  }

  def decodeYaml(yaml: String) = parse(yaml).flatMap(_.as[FeatureSchema])
}
