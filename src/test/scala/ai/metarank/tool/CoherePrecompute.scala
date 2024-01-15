package ai.metarank.tool

import ai.metarank.model.Event.ItemEvent
import ai.metarank.model.Field.StringField
import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.{Logging, RanklensEvents}
import cats.effect.kernel.Resource
import cats.effect.{ExitCode, IO, IOApp}
import fs2.io.file.{Files, Path}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.http4s.{
  AuthScheme,
  Credentials,
  Entity,
  EntityDecoder,
  EntityEncoder,
  Header,
  Headers,
  MediaType,
  Method,
  Request,
  Uri
}
import org.http4s.client.Client
import io.circe.syntax._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.`Content-Type`

import scala.concurrent.duration._

object CoherePrecompute extends IOApp with Logging {
  override def run(args: List[String]): IO[ExitCode] = args match {

    case token :: Nil =>
      for {
        items <- fs2
          .Stream(RanklensEvents(): _*)
          .collect { case e: ItemEvent => e }
          .flatMap(e => fs2.Stream.fromOption[IO](parse(e)))
          .compile
          .toList
        _ <- info(s"loaded ${items.size}")
        _ <- makeClient().use(client =>
          fs2
            .Stream(items: _*)
            .evalMap { case (item, text) => info(s"${item.value}: $text") *> query(token, client, item, text) }
            .map { case (id, emb) => (List(id.value) ++ emb.map(_.toString)).mkString("", ",", "\n") }
            .through(fs2.text.utf8.encode)
            .through(Files[IO].writeAll(Path("/tmp/cohere-large.csv")))
            .compile
            .toList
        )
      } yield {
        ExitCode.Success
      }

    case _ => IO.raiseError(new Exception("token needed"))
  }

  def parse(e: ItemEvent): Option[(ItemId, String)] = for {
    title <- e.fields.collectFirst { case StringField("title", value) => value }
    desc  <- e.fields.collectFirst { case StringField("description", value) => value }
  } yield {
    e.item -> s"$title $desc"
  }

  def query(token: String, client: Client[IO], id: ItemId, str: String): IO[(ItemId, Array[Float])] =
    client
      .expect[CohereResponse](makeRequest(token, CohereRequest("large", List(str))))
      .map(resp => id -> resp.embeddings(0).toArray)

  def makeClient(): Resource[IO, Client[IO]] =
    EmberClientBuilder
      .default[IO]
      .withTimeout(10.second)
      .build

  def makeRequest(token: String, req: CohereRequest) = {
    Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString("https://api.cohere.ai/v1/embed")
    ).withEntity(req.asJson)
      .withHeaders(org.http4s.headers.Authorization(Credentials.Token(AuthScheme.Bearer, token)))
      .withContentType(`Content-Type`(MediaType.application.json))

  }

  case class CohereRequest(model: String, texts: List[String])
  implicit val requestEncoder: Encoder[CohereRequest]        = deriveEncoder
  implicit val requestJson: EntityEncoder[IO, CohereRequest] = jsonEncoderOf[CohereRequest]

  case class CohereResponse(id: String, texts: List[String], embeddings: List[List[Float]])
  implicit val responseDecoder: Decoder[CohereResponse]        = deriveDecoder
  implicit val responseJson: EntityDecoder[IO, CohereResponse] = jsonOf[IO, CohereResponse]
}
