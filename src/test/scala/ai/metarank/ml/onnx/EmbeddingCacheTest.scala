package ai.metarank.ml.onnx

import better.files.File
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Try

class EmbeddingCacheTest extends AnyFlatSpec with Matchers {
  it should "load embeddings from a file" in {
    val file = File.newTemporaryFile("csv", ".csv")
    file.writeText("foo,1,2,3\nbar,4,5,6")
    val result = EmbeddingCache.fromCSV(file.toString(), ',', 3).unsafeRunSync()
    result.cache.size shouldBe 2
    file.delete()
  }

  it should "complain on dim mismatch" in {
    val file = File.newTemporaryFile("csv", ".csv")
    file.writeText("foo,1,2,3\nbar,4,5,6,7")
    val result = Try(EmbeddingCache.fromCSV(file.toString(), ',', 3).unsafeRunSync())
    result.isFailure shouldBe true
    file.delete()
  }
}
