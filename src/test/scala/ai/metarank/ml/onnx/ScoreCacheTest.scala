package ai.metarank.ml.onnx

import ai.metarank.ml.onnx.ScoreCache.ItemQueryKey
import ai.metarank.model.Identifier.ItemId
import better.files.File
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ScoreCacheTest extends AnyFlatSpec with Matchers {
  it should "load scores" in {
    val csv =
      """foo,p1,0.1
        |bar,p2,0.2""".stripMargin
    val file = File.newTemporaryFile("csv", ".csv").deleteOnExit()
    file.writeText(csv)
    val cache = ScoreCache.fromCSV(file.toString(), ',', 0).unsafeRunSync()
    cache shouldBe ScoreCache(Map(ItemQueryKey(ItemId("p1"), "foo") -> 0.1f, ItemQueryKey(ItemId("p2"), "bar") -> 0.2f))
  }
}
