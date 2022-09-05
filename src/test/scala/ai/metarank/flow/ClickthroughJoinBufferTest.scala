package ai.metarank.flow

import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.{EventId, Timestamp}
import ai.metarank.util.{TestFeatureMapping, TestInteractionEvent, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class ClickthroughJoinBufferTest extends AnyFlatSpec with Matchers {
  lazy val mapping = TestFeatureMapping()
  lazy val state   = MemPersistence(mapping.schema)

  it should "join rankings and interactions" in {
    val buffer = ClickthroughJoinBuffer(conf = ClickthroughJoinConfig(), store = state, mapping = mapping)
    val now    = Timestamp.now
    buffer.process(TestRankingEvent(List("p1")).copy(id = EventId("i1"), timestamp = now)).unsafeRunSync()
    buffer.process(TestInteractionEvent("p1", "i1").copy(timestamp = now.plus(1.second))).unsafeRunSync()
    buffer.tick(now.plus(1.days)).unsafeRunSync()
    Thread.sleep(100)
    val cts  = buffer.flushQueue().unsafeRunSync()
    val cts2 = state.cts.getall().compile.toList.unsafeRunSync()
    cts shouldNot be(empty)
  }
}
