package ai.metarank.ml

import ai.metarank.config.ModelConfig
import ai.metarank.model.TrainValues
import ai.metarank.util.TestClickthroughValues
import cats.effect.unsafe.implicits.global
import org.scalatest.Suite
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait PredictorSuite[C <: ModelConfig, T <: Context, M <: Model[T]] extends AnyFlatSpec with Matchers {
  this: Suite =>
  def predictor: Predictor[C, T, M]
  def request(n: Int): T
  def cts: List[TrainValues] = List(
    TestClickthroughValues(List("p1", "p2", "p3")),
    TestClickthroughValues(List("p2", "p4", "p5")),
    TestClickthroughValues(List("p1", "p2", "p3")),
    TestClickthroughValues(List("p6", "p2", "p3"))
  )

  it should "be created from a stream of click-throughs" in {
    val rec = predictor.fit(fs2.Stream.apply(cts: _*)).unsafeRunSync()
    val req = rec.predict(request(10)).unsafeRunSync()
    req.items.toList shouldNot be(empty)
  }

  it should "save-restore" in {
    val rec     = predictor.fit(fs2.Stream.apply(cts: _*)).unsafeRunSync()
    val bytes   = rec.save()
    val restore = predictor.load(bytes)
    val req     = restore.flatMap(_.predict(request(10))).unsafeRunSync()
    req.items.toList shouldNot be(empty)
  }

}
