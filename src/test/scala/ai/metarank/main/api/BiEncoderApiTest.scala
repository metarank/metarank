package ai.metarank.main.api

import ai.metarank.api.routes.inference.BiEncoderApi
import ai.metarank.api.routes.inference.BiEncoderApi.{BiencoderRequest, BiencoderResponse}
import ai.metarank.ml.onnx.ModelHandle.HuggingFaceHandle
import ai.metarank.ml.onnx.encoder.EncoderConfig.BiEncoderConfig
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.{Entity, Method, Request, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scodec.bits.ByteVector
import io.circe.parser._
import io.circe.syntax._

class BiEncoderApiTest extends AnyFlatSpec with Matchers {
  lazy val service = BiEncoderApi
    .create(
      Map(
        "test" -> BiEncoderConfig(
          model = Some(HuggingFaceHandle("metarank", "all-MiniLM-L6-v2")),
          dim = 384
        )
      ),
      existing = Nil
    )
    .unsafeRunSync()

  it should "encode things" in {
    val request  = BiencoderRequest(List("hello", "worls")).asJson.noSpaces
    val response = post("http://localhost/inference/encoder/test", request)
    response.map(_.embeddings.map(_.length)) shouldBe Right(List(384, 384))
  }

  def post(uri: String, payload: String): Either[Throwable, BiencoderResponse] = {
    val request = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString(uri),
      entity = Entity.strict(ByteVector(payload.getBytes))
    )
    val response = service.routes.apply(request).value.unsafeRunSync()
    response.map(r => new String(r.entity.body.compile.toList.unsafeRunSync().toArray)) match {
      case None       => Left(new Exception("empty response"))
      case Some(json) => decode[BiencoderResponse](json)
    }
  }

}
