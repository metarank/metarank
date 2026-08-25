package ai.metarank.ml.onnx

import ai.metarank.ml.onnx.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.metarank.ml.onnx.ModelHandleTest.HandleTest
import io.circe.Decoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*
import io.circe.generic.semiauto.*

class ModelHandleTest extends AnyFlatSpec with Matchers {
  it should "decode HF handle" in {
    parse("metarank/foo") shouldBe Right(HuggingFaceHandle("metarank", "foo"))
  }

  it should "decode local handle with single slash" in {
    parse("file://tmp/file") shouldBe Right(LocalModelHandle("/tmp/file"))
  }

  it should "decode local handle with double slash" in {
    parse("file:///tmp/file") shouldBe Right(LocalModelHandle("/tmp/file"))
  }

  def parse(handle: String): Either[Throwable, ModelHandle] = {
    decode[HandleTest](s"""{"handle": "$handle"}""").map(_.handle)
  }
}

object ModelHandleTest {
  case class HandleTest(handle: ModelHandle)
  given handleDecoder: Decoder[HandleTest] = deriveDecoder
}
