package me.dfdx.metarank.store.state.codec

import com.google.common.io.ByteStreams
import me.dfdx.metarank.store.state.codec.CodecDerivationTest.Foo
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.language.higherKinds

class CodecDerivationTest extends AnyFlatSpec with Matchers {

  it should "derive codec for strings" in {
    val foo = Codec.gen[Foo]
    roundtrip(foo, Foo("str", 1))
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
