package ai.metarank.util

import ai.metarank.util.DelimitedPair.DotDelimitedPair
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DelimitedPairTest extends AnyFlatSpec with Matchers {
  it should "fail on empty" in {
    DotDelimitedPair.unapply("") shouldBe empty
  }

  it should "fail on .foo" in {
    DotDelimitedPair.unapply(".foo") shouldBe empty
  }

  it should "fail on foo." in {
    DotDelimitedPair.unapply("foo.") shouldBe empty
  }

  it should "parse dot delimited" in {
    DotDelimitedPair.unapply("foo.bar") shouldBe Some(("foo", "bar"))
  }

  it should "parse multiple dots delimited" in {
    DotDelimitedPair.unapply("foo.bar.baz") shouldBe Some(("foo", "bar.baz"))
  }
}
