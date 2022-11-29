package ai.metarank.model

import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.{CategoryValue, SingleValue, VectorValue}
import io.circe.DecodingFailure
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import io.circe.parser._

class MValueJsonTest extends AnyFlatSpec with Matchers {
  it should "encode single value" in {
    enc(SingleValue(FeatureName("foo"), 1)) shouldBe """{"foo":1.0}"""
  }
  it should "encode missing" in {
    enc(SingleValue.missing(FeatureName("foo"))) shouldBe """{"foo":null}"""
  }

  it should "encode vector value" in {
    enc(VectorValue(FeatureName("foo"), Array(1.0), 1)) shouldBe """{"foo":[1.0]}"""
  }

  it should "encode missing vector" in {
    enc(VectorValue.missing(FeatureName("foo"), 1)) shouldBe """{"foo":[null]}"""
  }

  it should "encode cat value" in {
    enc(CategoryValue(FeatureName("foo"), "a", 1)) shouldBe """{"foo":"a@1"}"""
  }

  it should "decode single" in {
    dec("""{"foo":1.0}""") shouldBe Right(SingleValue(FeatureName("foo"), 1.0))
  }

  it should "decode missing" in {
    dec("""{"foo":null}""") shouldBe Right(SingleValue.missing(FeatureName("foo")))
  }

  it should "decode vector" in {
    dec("""{"foo":[1.0]}""") shouldBe Right(VectorValue(FeatureName("foo"), Array(1.0), 1))
  }

  it should "decode missing vector" in {
    dec("""{"foo":[null]}""") shouldBe Right(VectorValue.missing(FeatureName("foo"), 1))
  }

  it should "decode cat value" in {
    dec("""{"foo":"a@1"}""") shouldBe Right(CategoryValue(FeatureName("foo"), "a", 1))
  }

  def enc(value: MValue) = {
    List[MValue](value).asJson.noSpaces
  }

  def dec(str: String): Either[io.circe.Error, MValue] = {
    decode[List[MValue]](str) match {
      case Left(value)      => Left(value)
      case Right(head :: _) => Right(head)
      case _                => Left(DecodingFailure("ops", Nil))
    }
  }
}
