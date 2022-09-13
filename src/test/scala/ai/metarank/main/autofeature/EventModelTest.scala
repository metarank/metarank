package ai.metarank.main.autofeature

import ai.metarank.main.command.autofeature.FieldStat.StringFieldStat
import ai.metarank.main.command.autofeature.{EventModel, InteractionStat, ItemFieldStat}
import ai.metarank.model.EventId
import ai.metarank.model.Field.StringField
import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.{TestInteractionEvent, TestItemEvent, TestRankingEvent}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EventModelTest extends AnyFlatSpec with Matchers {
  it should "accept events" in {
    val events = List(
      TestItemEvent("p1", List(StringField("color", "red"))),
      TestItemEvent("p2", List(StringField("color", "green"))),
      TestRankingEvent(List("p1", "p2")).copy(id = EventId("1")),
      TestInteractionEvent("p1", "1").copy(`type` = "click"),
      TestInteractionEvent("p2", "1").copy(`type` = "cart")
    )
    val model = events.foldLeft(EventModel())((model, event) => model.refresh(event))

    model shouldBe EventModel(
      items = Set(ItemId("p1"), ItemId("p2")),
      itemFields = ItemFieldStat(strings = Map("color" -> StringFieldStat(Map("red" -> 1, "green" -> 1)))),
      interactions = InteractionStat(Map("click" -> 1, "cart" -> 1))
    )
  }
}
