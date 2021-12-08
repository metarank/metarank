package ai.metarank.model

import ai.metarank.model.MValue.{SingleValue, VectorValue}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import io.circe.parser._

class MValueJsonTest extends AnyFlatSpec with Matchers {
  it should "encode single value" in {
    val value: MValue = SingleValue("foo", 1)
    value.asJson.noSpaces shouldBe """{"name":"foo","value":1.0}"""
  }

  it should "encode vector value" in {
    val value: MValue = VectorValue(List("foo"), Array(1.0), 1)
    value.asJson.noSpaces shouldBe """{"names":["foo"],"values":[1.0]}"""
  }

  it should "decode single" in {
    decode[MValue]("""{"name":"foo","value":1.0}""") shouldBe Right(SingleValue("foo", 1.0))
  }

  it should "decode vector" in {
    decode[MValue]("""{"names":["foo"],"values":[1.0]}""") should matchPattern {
      case Right(VectorValue(List("foo"), _, 1)) =>
    }
  }
}
