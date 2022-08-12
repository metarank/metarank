package ai.metarank.model

import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.{CategoryValue, SingleValue, VectorValue}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import io.circe.parser._

class MValueJsonTest extends AnyFlatSpec with Matchers {
  it should "encode single value" in {
    val value: MValue = SingleValue(FeatureName("foo"), 1)
    value.asJson.noSpaces shouldBe """{"name":"foo","value":1.0}"""
  }

  it should "encode vector value" in {
    val value: MValue = VectorValue(FeatureName("foo"), Array(1.0), 1)
    value.asJson.noSpaces shouldBe """{"name":"foo","values":[1.0]}"""
  }

  it should "encode cat value" in {
    val value: MValue = CategoryValue(FeatureName("foo"), 1)
    value.asJson.noSpaces shouldBe """{"name":"foo","index":1}"""
  }

  it should "decode single" in {
    decode[MValue]("""{"name":"foo","value":1.0}""") shouldBe Right(SingleValue(FeatureName("foo"), 1.0))
  }

  it should "decode vector" in {
    decode[MValue]("""{"name":"foo","values":[1.0]}""") shouldBe Right(VectorValue(FeatureName("foo"), Array(1.0), 1))
  }

  it should "decode cat value" in {
    decode[MValue]("""{"name":"foo","index":1}""") shouldBe Right(CategoryValue(FeatureName("foo"), 1))
  }
}
