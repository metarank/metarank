package me.dfdx.metarank.store.state.codec

import com.google.common.io.ByteStreams
import me.dfdx.metarank.model.Field.{BooleanField, NumericField, NumericListField, StringField, StringListField}
import me.dfdx.metarank.model.{Field, Nel}
import me.dfdx.metarank.store.state.codec.CodecDerivationTest.Foo
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.language.higherKinds

class CodecDerivationTest extends AnyFlatSpec with Matchers {

  it should "derive codec for strings" in {
    val foo = Codec.gen[Foo]
    roundtrip(foo, Foo("str", 1))
  }

  it should "derive codecs for string fields" in {
    roundtrip(Codec.gen[StringField], StringField("foo", "bar"))
  }

  it should "derive codecs for string list fields" in {
    import Codec._
    roundtrip(Codec.gen[StringListField], StringListField("foo", Nel("bar")))
  }

  it should "derive codecs for bool fields" in {
    roundtrip(Codec.gen[BooleanField], BooleanField("foo", true))
  }

  it should "derive codecs for numeric fields" in {
    roundtrip(Codec.gen[NumericField], NumericField("foo", 1.0))
  }

  it should "derive codecs for numeric list fields" in {
    roundtrip(Codec.gen[NumericListField], NumericListField("foo", Nel(1.0)))
  }

  it should "derive codec for field" in {
    val c = Codec.gen[Field]
    roundtrip(c, NumericField("foo", 1.0))
    roundtrip(c, StringField("foo", "1.0"))
  }

  def roundtrip[T](codec: Codec[T], value: T) = {
    val buffer = ByteStreams.newDataOutput()
    codec.write(value, buffer)
    val result = codec.read(ByteStreams.newDataInput(buffer.toByteArray))
    result shouldBe value
  }
}

object CodecDerivationTest {
  case class Foo(str: String, i: Int)
}
