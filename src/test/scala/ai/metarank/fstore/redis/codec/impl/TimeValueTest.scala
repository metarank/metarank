package ai.metarank.fstore.redis.codec.impl

import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import ai.metarank.model.Scalar.SString
import ai.metarank.model.Timestamp
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TimeValueTest extends AnyFlatSpec with Matchers with BinCodecTest {
  it should "do timevalues" in {
    roundtrip(TimeValueCodec, TimeValue(Timestamp.now, SString("foo")))
  }
}
