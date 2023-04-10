package ai.metarank.ml.onnx

import ai.metarank.ml.onnx.encoder.Encoder
import ai.metarank.ml.onnx.encoder.EncoderType.CsvEncoderType
import better.files.File
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CSVEncoderTest extends AnyFlatSpec with Matchers {
  lazy val encoder = Encoder
    .create(
      CsvEncoderType(
        itemFieldCache = makeCache("id1,1,2,3\nid2,4,5,6"),
        rankingFieldCache = makeCache("foo,1,1,1\nbar,2,2,2"),
        dim = 3
      )
    )
    .unsafeRunSync()

  it should "use cache for embeddings" in {
    encoder.encode("foo").map(_.toList) shouldBe Some(List(1.0f, 1.0f, 1.0f))
    encoder.encode("id1", "whatever").map(_.toList) shouldBe Some(List(1.0f, 2.0f, 3.0f))
  }

  it should "return empty response on missing values" in {
    encoder.encode("whatever") shouldBe None
    encoder.encode("id10", "whatever") shouldBe None
  }

  def makeCache(text: String) = {
    val file = File.newTemporaryFile("csv").deleteOnExit()
    file.writeText(text)
    file.toString()
  }
}
