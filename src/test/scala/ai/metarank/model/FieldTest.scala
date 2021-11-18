package ai.metarank.model

import ai.metarank.model.Field.{BooleanField, NumberField, NumberListField, StringField, StringListField}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser._

class FieldTest extends AnyFlatSpec with Matchers {
  it should "decode string fields" in {
    decode[Field]("""{"name":"title", "value": "foo"}""") shouldBe Right(StringField("title", "foo"))
  }
  it should "decode numeric fields" in {
    decode[Field]("""{"name":"field", "value": 1.0}""") shouldBe Right(NumberField("field", 1.0))
  }
  it should "decode bool fields" in {
    decode[Field]("""{"name":"field", "value": true}""") shouldBe Right(BooleanField("field", true))
  }
  it should "decode string list fields" in {
    decode[Field]("""{"name":"field", "value": ["foo","bar"]}""") shouldBe Right(
      StringListField("field", List("foo", "bar"))
    )
  }
  it should "decode num list fields" in {
    decode[Field]("""{"name":"field", "value": [1,2,3]}""") shouldBe Right(NumberListField("field", List(1, 2, 3)))
  }
  it should "fail on null fields" in {
    decode[Field]("""{"name":"title", "value": null}""") shouldBe a[Left[_, _]]
  }
  it should "fail on missing fields" in {
    decode[Field]("""{"name":"title"}""") shouldBe a[Left[_, _]]
  }
  it should "fail on wrong list types fields" in {
    decode[Field]("""{"name":"title", "value": [true, false]}""") shouldBe a[Left[_, _]]
  }
}
