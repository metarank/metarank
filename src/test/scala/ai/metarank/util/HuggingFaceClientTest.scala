package ai.metarank.util

import ai.metarank.ml.onnx.{HuggingFaceClient, ModelHandle}
import ai.metarank.ml.onnx.HuggingFaceClient.ModelResponse
import ai.metarank.ml.onnx.HuggingFaceClient.ModelResponse.Sibling
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HuggingFaceClientTest extends AnyFlatSpec with Matchers {
  it should "fetch metadata" in {
    val model = HuggingFaceClient
      .create()
      .use(client => client.model(ModelHandle("metarank", "all-MiniLM-L6-v2")))
      .unsafeRunSync()
    model.siblings should contain(Sibling("vocab.txt"))
  }

  it should "fetch files" in {
    val vocab =
      HuggingFaceClient
        .create()
        .use(_.modelFile(ModelHandle("metarank", "all-MiniLM-L6-v2"), "vocab.txt"))
        .unsafeRunSync()
    vocab.length should be > (100)
  }
}
