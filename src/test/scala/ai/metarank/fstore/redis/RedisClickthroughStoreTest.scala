package ai.metarank.fstore.redis

import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.{Clickthrough, EventId, ItemValue}
import ai.metarank.util.{TestInteractionEvent, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RedisClickthroughStoreTest extends AnyFlatSpec with Matchers with RedisTest {
  lazy val stream = RedisClickthroughStore(client, client2)

  val rank      = TestRankingEvent(List("p1")).copy(id = EventId("1"))
  val itemValue = ItemValue(ItemId("p1"), List(SingleValue(FeatureName("foo"), 1)))

  it should "read empty" in {
    val result = stream.getall().compile.toList.unsafeRunSync()
    result shouldBe Nil
  }

  it should "write rankings" in {
    stream.putRanking(rank).unsafeRunSync()
    val ct = stream.getClickthrough(rank.id).unsafeRunSync()
    ct shouldBe Some(Clickthrough(rank.timestamp, rank.items.toList.map(_.id), Nil))
  }

  it should "write values" in {
    stream.putValues(rank.id, List(itemValue)).unsafeRunSync()
  }

  it should "read stream of events" in {
    for {
      i <- 0 until 1000
    } {
      val id = EventId(i.toString)
      stream.putRanking(TestRankingEvent(List("p1")).copy(id = id)).unsafeRunSync()
      stream.putInteraction(id, ItemId("p1"), "click").unsafeRunSync()
      stream.putValues(id, List(itemValue)).unsafeRunSync()
    }
    val read = stream.getall().compile.toList.unsafeRunSync()
    read.size shouldBe 1000
    read.forall(_.values.size == 1) shouldBe true
    read.forall(_.ct.interactions.size == 1) shouldBe true
  }

}
