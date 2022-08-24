package ai.metarank.util

import ai.metarank.model.Identifier.ItemId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class KendallCorrelationTest extends AnyFlatSpec with Matchers {
  it should "return 1 for correlated inputs" in {
    kendall(List("a", "b", "c"), List("a", "b", "c")) shouldBe 1.0
  }

  it should "deal with partially correlated inputs" in {
    kendall(List("a", "b", "c", "d"), List("a", "c", "b", "d")) shouldBe 0.666 +- 0.01
  }

  it should "return -1 for reverse-correlated inputs" in {
    kendall(List("a", "b", "c"), List("c", "b", "a")) shouldBe -1.0
  }

  it should "deal with new items" in {
    kendall(List("a", "b", "c"), List("d", "e", "f")) shouldBe 0.0
  }

  def kendall(a: List[String], b: List[String]) = KendallCorrelation(a.map(ItemId.apply), b.map(ItemId.apply))
}
