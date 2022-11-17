package ai.metarank.flow

import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.{Clickthrough, EventId, Timestamp}
import ai.metarank.model.Identifier.{ItemId, UserId}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ImpressionInjectTest extends AnyFlatSpec with Matchers {
  val ct = Clickthrough(
    id = EventId("i1"),
    ts = Timestamp.now,
    user = Some(UserId("u1")),
    session = None,
    items = List(ItemId("p1"), ItemId("p2"), ItemId("p3"), ItemId("p4")),
    interactions = List(TypedInteraction(ItemId("p2"), "click"))
  )

  it should "inject impressions on single click" in {
    val result = ImpressionInject.process(ct)
    result.map(_.item) shouldBe List(ItemId("p1"), ItemId("p2"))
  }

  it should "inject impressions on two clicks" in {
    val result = ImpressionInject.process(
      ct.copy(interactions =
        List(
          TypedInteraction(ItemId("p3"), "click"),
          TypedInteraction(ItemId("p1"), "click")
        )
      )
    )
    result.map(_.item) shouldBe List(ItemId("p1"), ItemId("p2"), ItemId("p3"))

  }
}
