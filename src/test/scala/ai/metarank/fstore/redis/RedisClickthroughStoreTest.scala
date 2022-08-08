package ai.metarank.fstore.redis

import ai.metarank.model.{Clickthrough, EventId}
import ai.metarank.util.{TestInteractionEvent, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RedisClickthroughStoreTest extends AnyFlatSpec with Matchers with RedisTest {
  lazy val stream = RedisClickthroughStore(client)

  it should "read empty" in {
    val result = stream.getall().compile.toList.unsafeRunSync()
    result shouldBe Nil
  }

  it should "write rankings" in {
    val rank = TestRankingEvent(List("p1")).copy(id = EventId("1"))
    stream.put(Clickthrough(rank)).unsafeRunSync()
    val ct = stream.get(rank.id).unsafeRunSync()
    ct shouldBe Some(Clickthrough(rank, Nil))
  }

  it should "read stream of events" in {
    for {
      i <- 0 until 1000
    } {
      stream.put(Clickthrough(TestRankingEvent(List("p1")).copy(id = EventId(i.toString)))).unsafeRunSync()
    }
    val read = stream.getall().compile.toList.unsafeRunSync()
    read.size shouldBe 1000
  }

}
