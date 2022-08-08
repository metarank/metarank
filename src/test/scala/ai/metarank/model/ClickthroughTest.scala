package ai.metarank.model

import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.{TestInteractionEvent, TestRankingEvent}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ClickthroughTest extends AnyFlatSpec with Matchers {
  it should "select impressions on first click" in {
    val ct = Clickthrough(TestRankingEvent(List("p1", "p2", "p3")))
    ct.impressions(TestInteractionEvent("p2", "x")) shouldBe List(ItemId("p1"), ItemId("p2"))
  }

  it should "select impressions on multi click" in {
    val ct     = Clickthrough(TestRankingEvent(List("p1", "p2", "p3", "p4")))
    val click1 = TestInteractionEvent("p2", "x")
    ct.impressions(click1) shouldBe List(ItemId("p1"), ItemId("p2"))
    ct.withInteraction(click1).impressions(TestInteractionEvent("p4", "x")) shouldBe List(ItemId("p3"), ItemId("p4"))
  }
}
