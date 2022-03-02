package ai.metarank.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OneHotEncoderTest extends AnyFlatSpec with Matchers {
  it should "encode nil" in {
    OneHotEncoder.fromValues(Nil, List("1", "2", "3"), 3).toList shouldBe List(0.0, 0.0, 0.0)
  }

  it should "encode all ones" in {
    OneHotEncoder.fromValues(List("2", "3", "1"), List("1", "2", "3"), 3).toList shouldBe List(1.0, 1.0, 1.0)
  }
}
