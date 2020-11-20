package me.dfdx.metarank.store.state.codec

import com.google.common.io.ByteStreams
import me.dfdx.metarank.store.state.codec.KeyCodecDerivationTest.Foo
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class KeyCodecDerivationTest extends AnyFlatSpec with Matchers {

  it should "derive keycodec for strings" in {
    val foo = KeyCodec.gen[Foo]
    foo.write(Foo("str", 1))
  }
}

object KeyCodecDerivationTest {
  case class Foo(str: String, i: Int)
}
