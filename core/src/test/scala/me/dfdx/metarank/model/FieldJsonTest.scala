package me.dfdx.metarank.model

import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser._
import me.dfdx.metarank.model.Field.{BooleanField, NumericField, NumericListField, StringField, StringListField}

class FieldJsonTest extends AnyFlatSpec with Matchers {

  it should "fail on null field" in {
    val field = decode[Field]("""{"name":"foo","value":null}""")
    field shouldBe a[Left[_, _]]
  }

  it should "decode string field" in {
    val field = decode[Field]("""{"name":"foo","value":"bar"}""")
    field shouldBe Right(StringField("foo", "bar"))
  }

  it should "fail on empty string field" in {
    val field = decode[Field]("""{"name":"foo","value":""}""")
    field shouldBe a[Left[_, _]]
  }

  it should "decode string list field" in {
    val field = decode[Field]("""{"name":"foo","value":["bar"]}""")
    field shouldBe Right(StringListField("foo", NonEmptyList.one("bar")))
  }

  it should "fail on empty string list" in {
    val field = decode[Field]("""{"name":"foo","value":[]}""")
    field shouldBe a[Left[_, _]]
  }

  it should "fail on string list with empty string" in {
    val field = decode[Field]("""{"name":"foo","value":["","foo"]}""")
    field shouldBe a[Left[_, _]]
  }

  it should "decode numeric field" in {
    val field = decode[Field]("""{"name":"foo","value": 1.0}""")
    field shouldBe Right(NumericField("foo", 1.0))
  }

  it should "fail on nan numeric field" in {
    val field = decode[Field]("""{"name":"foo","value": NaN}""")
    field shouldBe a[Left[_, _]]
  }

  it should "fail on inf numeric field" in {
    val field = decode[Field]("""{"name":"foo","value": Inf}""")
    field shouldBe a[Left[_, _]]
  }

  it should "decode numeric list field" in {
    val field = decode[Field]("""{"name":"foo","value": [1.0, 2.0]}""")
    field shouldBe Right(NumericListField("foo", NonEmptyList.of(1.0, 2.0)))
  }

  it should "fail on empty numeric list" in {
    val field = decode[Field]("""{"name":"foo","value": []}""")
    field shouldBe a[Left[_, _]]
  }

  it should "fail on numeric list with NaN" in {
    val field = decode[Field]("""{"name":"foo","value": [1.0, NaN]}""")
    field shouldBe a[Left[_, _]]
  }

  it should "fail on numeric list with Inf" in {
    val field = decode[Field]("""{"name":"foo","value": [1.0, Inf]}""")
    field shouldBe a[Left[_, _]]
  }

  it should "fail on mixed type list" in {
    val field = decode[Field]("""{"name":"foo","value": [1.0, "foo"]}""")
    field shouldBe a[Left[_, _]]
  }

  it should "decode bool field" in {
    val field = decode[Field]("""{"name":"foo","value": true}""")
    field shouldBe Right(BooleanField("foo", true))
  }

}
