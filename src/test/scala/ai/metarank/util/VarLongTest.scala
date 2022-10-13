package ai.metarank.util

import com.google.common.io.ByteStreams
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class VarLongTest extends AnyFlatSpec with Matchers {
  it should "roundtrip 8-bit longs" in {
    roundtrip(7, 1)
  }

  it should "roundtrip 16-bit longs" in {
    roundtrip(math.round(math.pow(2, 8) + 1).toInt, 2)
  }

  it should "roundtrip 24-bit longs" in {
    roundtrip(math.round(math.pow(2, 16) + 1).toInt, 3)
  }

  it should "roundtrip 32-bit longs" in {
    roundtrip(math.round(math.pow(2, 24) + 1).toInt, 4)
  }

  it should "roundtrip negative values" in {
    roundtrip(-7, 10)
  }

  it should "rt timestamps" in {
    roundtrip(1665141244228L, 6)
  }

  def roundtrip(value: Long, expectedSize: Int) = {
    val out = ByteStreams.newDataOutput()
    VarNum.putVarLong(value, out);
    val in      = ByteStreams.newDataInput(out.toByteArray)
    val decoded = VarNum.getVarLong(in)
    decoded shouldBe value
    out.toByteArray.length shouldBe expectedSize
  }

}
