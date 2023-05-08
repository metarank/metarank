package ai.metarank.ml.onnx.sbert

import ai.metarank.ml.onnx.ModelHandle.LocalModelHandle
import ai.metarank.ml.onnx.distance.DistanceFunction.CosineDistance
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OnnxBiencoderTest extends AnyFlatSpec with Matchers {
  it should "match mpnet on python" in {
    val handle = LocalModelHandle("/home/shutty/code/metarank-huggingface/all-mpnet-base-v2")
    val session = OnnxSession
      .loadFromLocalDir(handle, 768, "pytorch_model.onnx", "vocab.txt")
      .unsafeRunSync()
    val enc = OnnxBiEncoder(session)
    val result = enc.embed(
      Array(
        "How many people live in Berlin?",
        "Berlin is well known for its museums.",
        "Berlin had a population of 3,520,031 registered inhabitants in an area of 891.82 square kilometers."
      )
    )
    val d1 = CosineDistance.dist(result(0), result(1).map(_.toFloat))
    d1 shouldBe 0.261 +- 0.001
    val d2 = CosineDistance.dist(result(0), result(2).map(_.toFloat))
    d2 shouldBe 0.183 +- 0.001
  }
}
