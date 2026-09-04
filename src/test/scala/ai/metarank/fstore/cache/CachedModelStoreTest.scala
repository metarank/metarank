package ai.metarank.fstore.cache

import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.fstore.cache.CachedModelStore.RetiredModels
import ai.metarank.ml.Model.RankModel
import ai.metarank.ml.rank.QueryRequest
import cats.effect.IO
import com.github.benmanes.caffeine.cache.RemovalCause
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CachedModelStoreTest extends AnyFlatSpec with Matchers {
  class ProbeModel(val name: String) extends RankModel {
    var closed                                  = false
    override def save()                         = None
    override def predict(request: QueryRequest) = IO.raiseError(new Exception("not used"))
    override def close(): Unit                  = closed = true
    override def isClosed(): Boolean            = closed
  }

  it should "park a replaced model instead of closing it" in {
    val model = new ProbeModel("standard")
    CachedModelStore.disposeModel(new RetiredModels(4))(ModelName("standard"), model, RemovalCause.REPLACED)
    model.closed shouldBe false
  }

  it should "close a model evicted for size or expiry" in {
    for (cause <- List(RemovalCause.EXPIRED, RemovalCause.SIZE, RemovalCause.EXPLICIT)) {
      val model = new ProbeModel("standard")
      CachedModelStore.disposeModel(new RetiredModels(4))(ModelName("standard"), model, cause)
      withClue(s"cause=$cause: ") { model.closed shouldBe true }
    }
  }

  it should "keep parked models open up to its capacity" in {
    val retired = new RetiredModels(2)
    val models  = List.fill(2)(new ProbeModel("standard"))
    models.foreach(retired.park)
    models.map(_.closed) shouldBe List(false, false)
    retired.size() shouldBe 2
  }

  it should "close the oldest parked model once capacity is exceeded" in {
    val retired = new RetiredModels(2)
    val models  = List.fill(4)(new ProbeModel("standard"))
    models.foreach(retired.park)
    // The two most recent stay open, so a request holding one still has a live model
    models.map(_.closed) shouldBe List(true, true, false, false)
    retired.size() shouldBe 2
  }

  it should "keep parking when a parked model throws on close" in {
    val retired = new RetiredModels(1)
    val bad = new ProbeModel("bad") {
      override def close(): Unit = throw new RuntimeException("boom")
    }
    val good = new ProbeModel("good")
    retired.park(bad)
    retired.park(good)
    good.closed shouldBe false
    retired.size() shouldBe 1
  }
}
