package ai.metarank.ml.rank

import ai.metarank.config.BoosterConfig.LightGBMConfig
import ai.metarank.ml.rank.LambdaMARTRanker.{LambdaMARTConfig, LambdaMARTModel}
import ai.metarank.model.Event.RankItem
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Timestamp
import better.files.Resource
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import io.github.metarank.ltrlib.booster.LightGBMBooster
import io.github.metarank.ltrlib.model.Query
import org.apache.commons.io.IOUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.util.Random

/** Regression for the native use-after-free: closing a LambdaMART model from another thread (Caffeine removal listener)
  * while a predict is running used to free the LightGBM booster mid-call and crash the JVM.
  */
class LambdaMARTModelCloseTest extends AnyFlatSpec with Matchers {
  val modelBytes = IOUtils.toByteArray(Resource.my.getAsStream("/ranklens/ranklens.model"))
  val features   = 32 // ranklens.model was trained on 32 features
  val conf = LambdaMARTConfig(
    backend = LightGBMConfig(),
    features = NonEmptyList.one(FeatureName("foo")),
    weights = Map("click" -> 1.0)
  )

  def loadModel() = LambdaMARTModel("lgbm", conf, LightGBMBooster.apply(modelBytes))

  def request(rows: Int): QueryRequest = QueryRequest(
    items = NonEmptyList.fromListUnsafe((0 until rows).map(i => RankItem(ItemId(s"p$i"))).toList),
    user = None,
    session = None,
    ts = Timestamp.now,
    query = Query(0, new Array[Double](rows), Array.fill(rows * features)(Random.nextDouble()))
  )

  it should "survive a close from another thread while predicting" in {
    // repeat with fresh boosters so the close lands at different points of the native call
    for (round <- 1 to 20) {
      val model      = loadModel()
      val req        = request(100)
      val successes  = new AtomicInteger(0)
      val unexpected = new AtomicReference[Option[Throwable]](None)
      val warmedUp   = new CountDownLatch(1)
      val sawClosed  = new CountDownLatch(1)

      val inference = new Thread(
        () => {
          var running = true
          while (running) {
            try {
              val scores = model.predict(req).unsafeRunSync()
              scores.items.size shouldBe 100
              successes.incrementAndGet()
              if (successes.get() == 10) warmedUp.countDown()
            } catch {
              case _: IllegalStateException => sawClosed.countDown(); running = false
              case e: Throwable             => unexpected.set(Some(e)); running = false
            }
          }
        },
        s"inference-$round"
      )
      val closer = new Thread(
        () => {
          warmedUp.await()
          model.close()
        },
        s"closer-$round"
      )

      inference.start()
      closer.start()
      closer.join()
      inference.join()

      withClue(s"round $round: ") {
        unexpected.get() shouldBe None
        successes.get() should be >= 10
        sawClosed.getCount shouldBe 0
        model.isClosed() shouldBe true
        an[IllegalStateException] should be thrownBy model.predict(req).unsafeRunSync()
      }
    }
  }
}
