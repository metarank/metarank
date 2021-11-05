package ai.metarank.config

import ai.metarank.model.FieldSchema
import ai.metarank.model.FieldSchema.NumberFieldSchema
import io.circe.Decoder
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FieldSchemaYamlTest extends AnyFlatSpec with Matchers {
  it should "parse correct schema" in {
    val yaml =
      s"""name: price
         |type: number
         |required: true""".stripMargin
    decodeYaml[FieldSchema](yaml) shouldBe Right(NumberFieldSchema("price", true))
  }

  it should "have required field to be optional" in {
    val yaml =
      s"""name: price
         |type: number""".stripMargin
    decodeYaml[FieldSchema](yaml) shouldBe Right(NumberFieldSchema("price", false))
  }

  it should "fail on unsupported types" in {
    val yaml =
      s"""name: price
         |type: cat""".stripMargin
    decodeYaml[FieldSchema](yaml) shouldBe a[Left[_, _]]
  }

  it should "decode full field schema" in {}

  def decodeYaml[T: Decoder](in: String) = for {
    parsed  <- parse(in)
    decoded <- parsed.as[T]
  } yield {
    decoded
  }
}
