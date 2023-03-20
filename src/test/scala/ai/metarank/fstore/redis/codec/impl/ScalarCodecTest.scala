package ai.metarank.fstore.redis.codec.impl

import ai.metarank.fstore.codec.impl.ScalarCodec
import ai.metarank.model.Scalar.{SBoolean, SDouble, SDoubleList, SString, SStringList}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ScalarCodecTest extends AnyFlatSpec with Matchers with BinCodecTest {
  it should "do strings" in {
    roundtrip(ScalarCodec, SString("foo"))
  }
  it should "do numbers" in {
    roundtrip(ScalarCodec, SDouble(1.0))
  }
  it should "do bools" in {
    roundtrip(ScalarCodec, SBoolean(true))
  }
  it should "do string lists" in {
    roundtrip(ScalarCodec, SStringList(List("foo", "bar")))
  }
  it should "do double lists" in {
    roundtrip(ScalarCodec, SDoubleList(Array(1.0, 2.3)))
  }

}
