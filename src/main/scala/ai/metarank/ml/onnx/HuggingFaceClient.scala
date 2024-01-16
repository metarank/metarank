package ai.metarank.ml.onnx

import ai.metarank.flow.PrintProgress
import ai.metarank.ml.onnx.HuggingFaceClient.ModelResponse
import ai.metarank.ml.onnx.HuggingFaceClient.ModelResponse.Sibling
import ai.metarank.ml.onnx.ModelHandle.HuggingFaceHandle
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.kernel.Resource
import io.circe.Codec
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder}
import org.http4s.{EntityDecoder, Request, Uri}
import org.http4s.client.Client
import org.http4s.circe._
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.ci.CIString
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.io.ByteArrayOutputStream
import scala.concurrent.duration._

case class HuggingFaceClient(client: Client[IO], endpoint: Uri) extends Logging {

  implicit val modelResponseDecoder: EntityDecoder[IO, ModelResponse] = jsonOf[IO, ModelResponse]

  def model(handle: HuggingFaceHandle) = for {
    request  <- IO(Request[IO](uri = endpoint / "api" / "models" / handle.ns / handle.name))
    _        <- info(s"sending HuggingFace API request $request")
    response <- client.expect[ModelResponse](request)
  } yield {
    response
  }

  def modelFile(handle: HuggingFaceHandle, fileName: String): IO[Array[Byte]] = {
    get(endpoint / handle.ns / handle.name / "resolve" / "main" / fileName)
  }

  def get(uri: Uri): IO[Array[Byte]] =
    client
      .stream(Request[IO](uri = uri))
      .evalTap(_ => info(s"sending HuggingFace API request for a file $uri"))
      .evalMap(response =>
        response.status.code match {
          case 200 =>
            info("HuggingFace API: HTTP 200") *> response.entity.body
              .through(PrintProgress.tap(None, "bytes"))
              .compile
              .foldChunks(new ByteArrayOutputStream())((acc, c) => {
                acc.writeBytes(c.toArray)
                acc
              })
              .map(_.toByteArray)
          case 302 =>
            response.headers.get(CIString("Location")) match {
              case Some(locations) =>
                Uri.fromString(locations.head.value) match {
                  case Left(value) => IO.raiseError(value)
                  case Right(uri)  => info("302 redirect") *> get(uri)
                }
              case None => IO.raiseError(new Exception(s"Got 302 redirect without location"))
            }
          case other => IO.raiseError(new Exception(s"HTTP code $other"))
        }
      )
      .compile
      .fold(new ByteArrayOutputStream())((acc, c) => {
        acc.writeBytes(c)
        acc
      })
      .map(_.toByteArray)
}

object HuggingFaceClient {
  val HUGGINGFACE_API_ENDPOINT = "https://huggingface.co"
  case class ModelResponse(id: String, siblings: List[Sibling])
  object ModelResponse {
    case class Sibling(rfilename: String)
  }

  implicit val modelSiblingCodec: Codec[Sibling]        = deriveCodec[Sibling]
  implicit val modelResponseCodec: Codec[ModelResponse] = deriveCodec[ModelResponse]

  def create(endpoint: String = HUGGINGFACE_API_ENDPOINT): Resource[IO, HuggingFaceClient] = {
    implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]
    for {
      uri    <- Resource.eval(IO.fromEither(Uri.fromString(endpoint)))
      client <- EmberClientBuilder.default[IO].withTimeout(200.seconds).build
    } yield {
      HuggingFaceClient(client, uri)
    }
  }

}
