package ai.metarank.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser._

class TimestampJsonTest extends AnyFlatSpec with Matchers {
  it should "decode unixtime" in {
    decode[Timestamp]("1671200049") shouldBe Right(Timestamp(1671200049000L))
    decode[Timestamp]("\"1671200049\"") shouldBe Right(Timestamp(1671200049000L))
  }

  it should "decode unixtime in millis" in {
    decode[Timestamp]("1671200049000") shouldBe Right(Timestamp(1671200049000L))
    decode[Timestamp]("\"1671200049000\"") shouldBe Right(Timestamp(1671200049000L))
  }

  it should "decode time in ISO" in {
    decode[Timestamp]("\"2022-12-16 14:14:09\"") shouldBe Right(Timestamp(1671200049000L))
  }

  it should "fail on broken string" in {
    decode[Timestamp]("\"foo\"") shouldBe a[Left[_, _]]
  }

  it should "fail on broken number" in {
    decode[Timestamp]("16712000490") shouldBe a[Left[_, _]]
  }
}
