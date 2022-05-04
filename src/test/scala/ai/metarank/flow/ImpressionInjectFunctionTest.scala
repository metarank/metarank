package ai.metarank.flow

import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, RankingEvent}
import ai.metarank.model.EventId
import ai.metarank.util.{FlinkTest, TestInteractionEvent, TestRankingEvent}
import org.apache.flink.api.common.RuntimeExecutionMode
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.apache.flink.api.scala._

import scala.concurrent.duration._

class ImpressionInjectFunctionTest extends AnyFlatSpec with Matchers with FlinkTest {
  it should "emit impressions" in {
    env.setRuntimeMode(RuntimeExecutionMode.BATCH)
    val result = env
      .fromCollection[FeedbackEvent](
        List(TestRankingEvent(List("p1", "p2", "p3")).copy(id = EventId("i1")), TestInteractionEvent("p2", "i1", Nil))
      )
      .keyBy(e =>
        e match {
          case int: InteractionEvent => int.ranking.getOrElse(EventId("0"))
          case rank: RankingEvent    => rank.id
        }
      )
      .process(new ImpressionInjectFunction("examine", 30.minutes))
      .executeAndCollect(100)
    result.collect { case e: InteractionEvent => e.item.value } shouldBe List("p1", "p2")
  }
}
