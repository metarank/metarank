package ai.metarank.fstore.codec.values

import ai.metarank.fstore.codec.impl.ScalarCodec
import ai.metarank.model.Scalar
import ai.metarank.model.Scalar.SString
import com.google.common.io.ByteStreams

class BinaryVCodecTest extends VCodecTest[Scalar] {
  override val codec    = BinaryVCodec(compress = true, ScalarCodec)
  override val instance = SString("yolo")

  it should "stop on a record with negative size" in {
    val bytes = ByteStreams.newDataOutput()
    bytes.writeInt(-1256367234)
    bytes.write(Array[Byte](1, 2, 3))
    codec.decodeDelimited(ByteStreams.newDataInput(bytes.toByteArray)) shouldBe Right(None)
  }

  it should "stop on a truncated record" in {
    val bytes = ByteStreams.newDataOutput()
    bytes.writeInt(100)
    bytes.write(new Array[Byte](10))
    codec.decodeDelimited(ByteStreams.newDataInput(bytes.toByteArray)) shouldBe Right(None)
  }

  it should "read valid records before a corrupted tail" in {
    val bytes = ByteStreams.newDataOutput()
    codec.encodeDelimited(instance, bytes)
    bytes.writeInt(-1)
    bytes.write(Array[Byte](1, 2, 3))
    val in = ByteStreams.newDataInput(bytes.toByteArray)
    codec.decodeDelimited(in) shouldBe Right(Some(instance))
    codec.decodeDelimited(in) shouldBe Right(None)
  }
}
