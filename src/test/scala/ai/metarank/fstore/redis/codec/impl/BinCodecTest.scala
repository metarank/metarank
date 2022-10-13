package ai.metarank.fstore.redis.codec.impl

import com.google.common.io.ByteStreams
import org.scalatest.Suite
import org.scalatest.matchers.should.Matchers

trait BinCodecTest { this: Suite with Matchers =>
  def roundtrip[T](codec: BinaryCodec[T], value: T) = {
    val out = ByteStreams.newDataOutput()
    codec.write(value, out)
    val in      = ByteStreams.newDataInput(out.toByteArray)
    val decoded = codec.read(in)
    decoded shouldBe value
  }
}
