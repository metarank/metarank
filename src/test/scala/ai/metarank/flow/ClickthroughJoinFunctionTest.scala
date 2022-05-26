package ai.metarank.flow

import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, RankingEvent}
import ai.metarank.model.{Clickthrough, EventId}
import ai.metarank.util.{FlinkTest, TestInteractionEvent, TestRankingEvent}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.findify.flink.api._
import io.findify.flink.api.extensions._

class ClickthroughJoinFunctionTest extends AnyFlatSpec with Matchers with FlinkTest {
  import DataStreamOps._
  import ai.metarank.mode.TypeInfos._

  it should "collapse rank+click" in {
    val result = env
      .fromCollection[FeedbackEvent](
        List(
          TestRankingEvent(List("p1", "p2", "p3")).copy(id = EventId("1")),
          TestInteractionEvent("p2", "1")
        )
      )
      .watermark(_.timestamp.ts)
      .keyingBy {
        case i: InteractionEvent => i.ranking.getOrElse(EventId("0"))
        case r: RankingEvent     => r.id
      }
      .process(ClickthroughJoinFunction())
      .executeAndCollect(10)
    result.map(_.ranking.id.value) shouldBe List("1")
    result.flatMap(_.ranking.items.toList.map(_.id.value)) shouldBe List("p1", "p2", "p3")
    result.flatMap(_.interactions.map(_.item.value)) shouldBe List("p2")
  }
}
