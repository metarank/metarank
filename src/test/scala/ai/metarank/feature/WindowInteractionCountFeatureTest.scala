package ai.metarank.feature

import ai.metarank.feature.WindowInteractionCountFeature.WindowInteractionCountSchema
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.Event.RankItem
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.{Key, Schema}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.Write.PeriodicIncrement
import ai.metarank.util.{TestInteractionEvent, TestItemEvent, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class WindowInteractionCountFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {
  val feature = WindowInteractionCountFeature(
    WindowInteractionCountSchema(
      name = FeatureName("cnt"),
      interaction = "click",
      bucket = 24.hours,
      periods = List(1),
      scope = ItemScopeType
    )
  )
  val store = MemPersistence(Schema(feature.states))

  it should "count item clicks" in {
    val event = TestInteractionEvent("e1", "e0").copy(`type` = "click", item = ItemId("p1"))
    val write = feature.writes(event, store).unsafeRunSync().toList
    write shouldBe List(
      PeriodicIncrement(Key(ItemScope(ItemId("p1")), FeatureName("cnt")), event.timestamp, 1)
    )
  }

  it should "ignore non-interaction events" in {
    feature.writes(TestItemEvent("p1"), store).unsafeRunSync().toList shouldBe Nil
    feature.writes(TestRankingEvent(List("p1")), store).unsafeRunSync().toList shouldBe Nil
  }

  it should "compute values" in {
    val values = process(
      List(
        TestInteractionEvent("e1", "e0").copy(`type` = "click", item = ItemId("p1")),
        TestInteractionEvent("e1", "e0").copy(`type` = "click", item = ItemId("p1")),
        TestInteractionEvent("e1", "e0").copy(`type` = "click", item = ItemId("p1"))
      ),
      feature.schema,
      TestRankingEvent(List("p1"))
    )
    values shouldBe List(List(VectorValue(FeatureName("cnt"), Array(3), 1)))
  }
}
