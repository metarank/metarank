package ai.metarank.fstore.redis

import ai.metarank.fstore.codec.StoreFormat.JsonStoreFormat
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.TrainValues.ClickthroughValues
import ai.metarank.model.{Clickthrough, EventId, ItemValue}
import ai.metarank.util.{TestClickthrough, TestInteractionEvent, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class RedisTrainStoreTest extends AnyFlatSpec with Matchers with RedisTest {

  it should "read empty" in {
    lazy val stream = RedisTrainStore(client, "a", JsonStoreFormat, 90.days)
    val result      = stream.getall().compile.toList.unsafeRunSync()
    result shouldBe Nil
  }

  it should "write and read clickthroughs" in {
    lazy val stream = RedisTrainStore(client, "b", JsonStoreFormat, 90.days)
    val ct          = List(ClickthroughValues(TestClickthrough(List("p1", "p2", "p3"), List("p2")), Nil))
    stream.put(ct).unsafeRunSync()
    val results = stream.getall().compile.toList.unsafeRunSync()
    results.size shouldBe 1
  }

  it should "read stream of events" in {
    lazy val stream = RedisTrainStore(client, "c", JsonStoreFormat, 90.days)
    for {
      i <- 0 until 1000
    } {
      val id = EventId(i.toString)
      val ct = List(
        ClickthroughValues(
          TestClickthrough(List("p1", "p2", "p3"), List("p2")).copy(id = id),
          List(ItemValue(ItemId("p1"), List(SingleValue(FeatureName("foo"), 1))))
        )
      )
      stream.put(ct).unsafeRunSync()
    }
    val read = stream.getall().compile.toList.unsafeRunSync().collect { case c: ClickthroughValues => c }
    read.size shouldBe 1000
    read.forall(_.values.size == 1) shouldBe true
    read.forall(_.ct.interactions.size == 1) shouldBe true
  }

}
