package ai.metarank.ml.onnx

import better.files.Resource
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SBERTTest extends AnyFlatSpec with Matchers {
  it should "produce embeddings for minilm-l6-v2" in {
    val sbert = SBERT(
      model = Resource.my.getAsStream("/sbert/sentence-transformer/all-MiniLM-L6-v2.onnx"),
      dic = Resource.my.getAsStream("/sbert/sentence-transformer/vocab.txt")
    )
    val result = sbert.embed("hello, world!")
    result.length shouldBe 384
  }
}
