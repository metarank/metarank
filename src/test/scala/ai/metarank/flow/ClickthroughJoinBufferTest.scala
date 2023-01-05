package ai.metarank.flow

import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.fstore.memory.{MemClickthroughStore, MemPersistence}
import ai.metarank.model.{EventId, Timestamp}
import ai.metarank.util.{TestFeatureMapping, TestInteractionEvent, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class ClickthroughJoinBufferTest extends AnyFlatSpec with Matchers {
  lazy val mapping = TestFeatureMapping()
  lazy val state   = MemPersistence(mapping.schema)
  lazy val cs      = MemClickthroughStore()

  it should "join rankings and interactions" in {
    val buffer = ClickthroughJoinBuffer(conf = ClickthroughJoinConfig(), values = state.values, cs, mapping = mapping)
    val now    = Timestamp.now
    buffer.process(TestRankingEvent(List("p1")).copy(id = EventId("i1"), timestamp = now)).unsafeRunSync()
    buffer.process(TestInteractionEvent("p1", "i1").copy(timestamp = now.plus(1.second))).unsafeRunSync()
    buffer.flushAll().unsafeRunSync()
    val cts = cs.getall().compile.toList.unsafeRunSync()
    cts shouldNot be(empty)
  }

  it should "not emit cts on no click" in {
    val buffer = ClickthroughJoinBuffer(conf = ClickthroughJoinConfig(), values = state.values, cs, mapping = mapping)
    val now    = Timestamp.now
    buffer.process(TestRankingEvent(List("p1")).copy(id = EventId("i1"), timestamp = now)).unsafeRunSync()
    buffer.cache.invalidateAll()
    Thread.sleep(100)
    val cts = buffer.flushQueue().unsafeRunSync()
    cts shouldBe empty
  }
}
