package ai.metarank.config

import ai.metarank.model.Event.InteractionEvent
import ai.metarank.model.{FeatureSchema, FieldName}
import ai.metarank.model.FeatureSchema.{BooleanFeatureSchema, NumberFeatureSchema, StringFeatureSchema, durationDecoder}
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.FieldName.{Interaction, Metadata}
import cats.data.NonEmptyList
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.{FiniteDuration, _}

class FeatureSchemaTest extends AnyFlatSpec with Matchers {
  it should "decode config for number" in {
    decodeYaml("name: price\ntype: number\nscope: item\nsource: metadata.price") shouldBe Right(
      NumberFeatureSchema("price", FieldName(Metadata, "price"), ItemScope)
    )
  }

  it should "decode config for number with refresh" in {
    decodeYaml("name: price\ntype: number\nscope: item\nsource: metadata.price\nrefresh: 1m") shouldBe Right(
      NumberFeatureSchema("price", FieldName(Metadata, "price"), ItemScope, Some(1.minute))
    )
  }

  it should "decode config for string" in {
    decodeYaml("name: price\ntype: string\nscope: item\nsource: metadata.price\nvalues: [\"foo\"]") shouldBe Right(
      StringFeatureSchema("price", FieldName(Metadata, "price"), ItemScope, NonEmptyList.one("foo"))
    )
  }

  it should "decode config for boolean" in {
    decodeYaml("name: price\ntype: boolean\nscope: item\nsource: metadata.price") shouldBe Right(
      BooleanFeatureSchema("price", FieldName(Metadata, "price"), ItemScope)
    )
  }

  it should "decode feature source" in {
    decodeYaml("name: price\ntype: boolean\nscope: item\nsource: interaction:click.foo") shouldBe Right(
      BooleanFeatureSchema("price", FieldName(Interaction("click"), "foo"), ItemScope)
    )
  }

  it should "decode durations" in {
    parse("1d").flatMap(_.as[FiniteDuration]) shouldBe Right(1.day)
    parse("1s").flatMap(_.as[FiniteDuration]) shouldBe Right(1.second)
    parse("1m").flatMap(_.as[FiniteDuration]) shouldBe Right(1.minute)
    parse("1h").flatMap(_.as[FiniteDuration]) shouldBe Right(1.hour)
  }

  def decodeYaml(yaml: String) = parse(yaml).flatMap(_.as[FeatureSchema])
}
