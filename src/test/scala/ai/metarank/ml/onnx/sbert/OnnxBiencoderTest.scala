package ai.metarank.ml.onnx.sbert

import ai.metarank.ml.onnx.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.metarank.ml.onnx.distance.DistanceFunction.CosineDistance
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OnnxBiencoderTest extends AnyFlatSpec with Matchers {
  it should "match minilm on python" in {
    val handle  = HuggingFaceHandle("metarank", "all-MiniLM-L6-v2")
    val session = OnnxSession.load(handle, 384).unsafeRunSync()
    val enc     = OnnxBiEncoder(session)
    val fp      = enc.embed(Array("copper frying pan"))
    val result = enc.embed(
      Array(
        "How many people live in Berlin?",
        "Berlin is well known for its museums.",
        "Berlin had a population of 3,520,031 registered inhabitants in an area of 891.82 square kilometers."
      )
    )
    val d1 = CosineDistance.dist(result(0), result(1).map(_.toFloat))
    d1 shouldBe 0.539 +- 0.001
    val d2 = CosineDistance.dist(result(0), result(2).map(_.toFloat))
    d2 shouldBe 0.738 +- 0.001
  }
}
