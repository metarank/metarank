package ai.metarank.config

import ai.metarank.model.Event.InteractionEvent
import ai.metarank.model.FeatureSchema
import ai.metarank.model.FeatureSchema.{BooleanFeatureSchema, NumberFeatureSchema, StringFeatureSchema, durationDecoder}
import ai.metarank.model.FeatureSource.{Interaction, Item}
import cats.data.NonEmptyList
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.{FiniteDuration, _}

class FeatureSchemaTest extends AnyFlatSpec with Matchers {
  it should "decode config for number" in {
    decodeYaml("name: price\ntype: number\nfield: price\nsource: item") shouldBe Right(
      NumberFeatureSchema("price", "price", Item)
    )
  }

  it should "decode config for number with refresh" in {
    decodeYaml("name: price\ntype: number\nfield: price\nsource: item\nrefresh: 1m") shouldBe Right(
      NumberFeatureSchema("price", "price", Item, Some(1.minute))
    )
  }

  it should "decode config for string" in {
    decodeYaml("name: price\ntype: string\nfield: price\nsource: item\nvalues: [\"foo\"]") shouldBe Right(
      StringFeatureSchema("price", "price", Item, NonEmptyList.one("foo"))
    )
  }

  it should "decode config for boolean" in {
    decodeYaml("name: price\ntype: boolean\nfield: price\nsource: item") shouldBe Right(
      BooleanFeatureSchema("price", "price", Item)
    )
  }

  it should "decode feature source" in {
    decodeYaml("name: price\ntype: boolean\nfield: foo\nsource: interaction:click") shouldBe Right(
      BooleanFeatureSchema("price", "foo", Interaction("click"))
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
