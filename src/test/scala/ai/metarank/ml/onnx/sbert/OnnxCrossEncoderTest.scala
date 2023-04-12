package ai.metarank.ml.onnx.sbert

import ai.metarank.ml.onnx.ModelHandle.HuggingFaceHandle
import ai.metarank.ml.onnx.sbert.OnnxCrossEncoder.SentencePair
import better.files.File
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OnnxCrossEncoderTest extends AnyFlatSpec with Matchers {
  it should "encode qp" in {
    val session = OnnxSession
      .loadFromHuggingFace(HuggingFaceHandle("metarank", "ce-msmarco-MiniLM-L6-v2"), "pytorch_model.onnx", "vocab.txt")
      .unsafeRunSync()
    val ce = OnnxCrossEncoder(session)
    val result = ce.encode(
      Array(
        SentencePair(
          "How many people live in Berlin?",
          "Berlin had a population of 3,520,031 registered inhabitants in an area of 891.82 square kilometers."
        ),
        SentencePair("How many people live in Berlin?", "Berlin is well known for its museums.")
      )
    )
    result(0) shouldBe 8.607141f +- 0.1f
    result(1) shouldBe -4.32008f +- 0.1f
  }
}
