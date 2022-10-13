package ai.metarank.util

import com.google.common.io.ByteStreams
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class VarIntTest extends AnyFlatSpec with Matchers {
  it should "roundtrip 8-bit ints" in {
    roundtrip(7, 1)
  }

  it should "roundtrip 16-bit ints" in {
    roundtrip(math.round(math.pow(2, 8) + 1).toInt, 2)
  }

  it should "roundtrip 24-bit ints" in {
    roundtrip(math.round(math.pow(2, 16) + 1).toInt, 3)
  }

  it should "roundtrip 32-bit ints" in {
    roundtrip(math.round(math.pow(2, 24) + 1).toInt, 4)
  }

  it should "roundtrip negative values" in {
    roundtrip(-7, 5)
  }

  def roundtrip(value: Int, expectedSize: Int) = {
    val out = ByteStreams.newDataOutput()
    VarNum.putVarInt(value, out);
    val in      = ByteStreams.newDataInput(out.toByteArray)
    val decoded = VarNum.getVarInt(in)
    decoded shouldBe value
    out.toByteArray.length shouldBe expectedSize
  }

}
