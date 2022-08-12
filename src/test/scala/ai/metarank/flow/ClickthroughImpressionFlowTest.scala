package ai.metarank.flow

import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.EventId
import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.{TestFeatureMapping, TestInteractionEvent, TestRankingEvent, TestSchema}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import fs2.Stream

class ClickthroughImpressionFlowTest extends AnyFlatSpec with Matchers {

  it should "inject impressions" in {
    val state = MemPersistence(TestFeatureMapping().schema)
    val flow  = ClickthroughImpressionFlow(state, TestFeatureMapping())
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
}
