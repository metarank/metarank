package ai.metarank.main.autofeature

import ai.metarank.main.command.autofeature.FieldStat.{NumericFieldStat, StringFieldStat}
import ai.metarank.main.command.autofeature.{EventModel, InteractionStat, ItemFieldStat}
import ai.metarank.model.Event.RankItem
import ai.metarank.model.EventId
import ai.metarank.model.Field.StringField
import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.{TestInteractionEvent, TestItemEvent, TestRankingEvent}
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EventModelTest extends AnyFlatSpec with Matchers {
  it should "accept events" in {
    val events = List(
      TestItemEvent("p1", List(StringField("color", "red"))),
      TestItemEvent("p2", List(StringField("color", "green"))),
      TestRankingEvent(List("p1", "p2")).copy(id = EventId("1")),
      TestInteractionEvent("p1", "1").copy(`type` = "click"),
      TestInteractionEvent("p2", "1").copy(`type` = "cart"),
      TestRankingEvent(List("p1", "p2"))
        .copy(
          id = EventId("2"),
          items = NonEmptyList.of(RankItem(ItemId("p1"), 2.0), RankItem(ItemId("p2"), 3.0))
        )
    )
    val model = events.foldLeft(EventModel())((model, event) => model.refresh(event))

    model shouldBe EventModel(
      eventCount = 6,
      items = Set(ItemId("p1"), ItemId("p2")),
      itemFields = ItemFieldStat(strings = Map("color" -> StringFieldStat(Map("red" -> 1, "green" -> 1)))),
      interactions = InteractionStat(Map("click" -> 1, "cart" -> 1)),
      rankFields = ItemFieldStat(nums = Map("relevancy" -> NumericFieldStat(List(3.0, 2.0, 1.0, 1.0))))
    )
  }
}
