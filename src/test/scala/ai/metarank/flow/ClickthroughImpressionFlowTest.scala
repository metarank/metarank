package ai.metarank.flow

import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.{Clickthrough, EventId}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.{TestFeatureMapping, TestInteractionEvent, TestRankingEvent, TestSchema}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import fs2.Stream

class ClickthroughImpressionFlowTest extends AnyFlatSpec with Matchers {

  val fm = TestFeatureMapping()

  it should "inject impressions" in {
    val state = MemPersistence(fm.schema)
    val flow  = ClickthroughImpressionFlow(state, fm)
    val rank  = TestRankingEvent(List("p1", "p2", "p3")).copy(id = EventId("1"))
    val click = TestInteractionEvent("p3", "1")
    val result = Stream
      .emits(List(rank, click))
      .through(flow.process)
      .compile
      .toList
      .unsafeRunSync()
    val imp1 = click.copy(`type` = "impression", item = ItemId("p1"))
    val imp2 = click.copy(`type` = "impression", item = ItemId("p2"))
    val imp3 = click.copy(`type` = "impression", item = ItemId("p3"))
    result shouldBe List(rank, click, imp1, imp2, imp3)
  }

  it should "select impressions on first click" in {
    val flow  = ClickthroughImpressionFlow(MemPersistence(fm.schema), fm)
    val event = TestRankingEvent(List("p1", "p2", "p3"))
    val ct    = Clickthrough(event.timestamp, event.items.toList.map(_.id))
    flow.impressions(ct, TestInteractionEvent("p2", "x")) shouldBe List(ItemId("p1"), ItemId("p2"))
  }

  it should "select impressions on multi click" in {
    val flow   = ClickthroughImpressionFlow(MemPersistence(fm.schema), fm)
    val event  = TestRankingEvent(List("p1", "p2", "p3", "p4"))
    val ct     = Clickthrough(event.timestamp, event.items.toList.map(_.id))
    val click1 = TestInteractionEvent("p2", "x")
    flow.impressions(ct, click1) shouldBe List(ItemId("p1"), ItemId("p2"))
    flow.impressions(ct.withInteraction(ItemId("p2"), "x"), TestInteractionEvent("p4", "x")) shouldBe List(
      ItemId("p3"),
      ItemId("p4")
    )
  }

}
