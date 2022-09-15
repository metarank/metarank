package ai.metarank.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class VersionTest extends AnyFlatSpec with Matchers {
  it should "parse release manifest" in {
    val result = Version("/testmanifest.mf")
    result shouldBe Some("0.5.3")
  }
}
