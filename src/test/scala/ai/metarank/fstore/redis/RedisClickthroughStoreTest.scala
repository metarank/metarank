package ai.metarank.fstore.redis

import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.{Clickthrough, ClickthroughValues, EventId, ItemValue}
import ai.metarank.util.{TestClickthrough, TestInteractionEvent, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RedisClickthroughStoreTest extends AnyFlatSpec with Matchers with RedisTest {

  it should "read empty" in {
    lazy val stream = RedisClickthroughStore(client, "a")
    val result      = stream.getall().compile.toList.unsafeRunSync()
    result shouldBe Nil
  }

  it should "write and read clickthrougts" in {
    lazy val stream = RedisClickthroughStore(client, "b")
    val ct          = List(ClickthroughValues(TestClickthrough(List("p1", "p2", "p3"), List("p2")), Nil))
    stream.put(ct).unsafeRunSync()
    val results = stream.getall().compile.toList.unsafeRunSync()
    results.size shouldBe 1
  }

  it should "read stream of events" in {
    lazy val stream = RedisClickthroughStore(client, "c")
    for {
      i <- 0 until 1000
    } {
      val id = EventId(i.toString)
      val ct = List(ClickthroughValues(TestClickthrough(List("p1", "p2", "p3"), List("p2")).copy(id = id), Nil))
      stream.put(ct).unsafeRunSync()
    }
    val read = stream.getall().compile.toList.unsafeRunSync()
    read.size shouldBe 1000
    read.forall(_.values.size == 1) shouldBe true
    read.forall(_.ct.interactions.size == 1) shouldBe true
  }

}
