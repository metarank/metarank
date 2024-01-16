package ai.metarank.tool

import ai.metarank.api.routes.RankApi.RankResponse
import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.model.Event.{ItemEvent, RankItem, RankingEvent}
import ai.metarank.model.Identifier.UserId
import ai.metarank.model.{Event, EventId, Timestamp}
import ai.metarank.source.FileEventSource
import ai.metarank.util.Logging
import better.files.File
import cats.data.NonEmptyList
import cats.effect.kernel.Resource
import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.{Entity, EntityDecoder, Method, Request, Uri}
import org.http4s.client.Client
import scodec.bits.ByteVector
import io.circe.syntax._
import cats.implicits._

import java.util.UUID
import scala.concurrent.duration._
import org.http4s.circe._
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

object StaticScoreTool extends IOApp with Logging {
  // loads the dataset and emits static scores

  implicit val responseJson: EntityDecoder[IO, RankResponse] = jsonOf[IO, RankResponse]

  override def run(args: List[String]): IO[ExitCode] = args match {
    case eventPath :: outPath :: endpoint :: _ =>
      val source = FileEventSource(FileInputConfig(eventPath)).stream
      for {
        ids      <- source.collect { case item: ItemEvent => item.item }.compile.toList
        _        <- info(s"Loaded ${ids.size} items")
        batches  <- IO(ids.grouped(128).toList)
        _        <- info(s"Formed ${batches.size} batches")
        endpoint <- IO.fromEither(Uri.fromString(endpoint))
        result <- makeClient().use(http => {
          batches
            .map(batch => {
              val json: Event = RankingEvent(
                id = EventId(UUID.randomUUID().toString),
                timestamp = Timestamp.now,
                user = Some(UserId("alice")),
                session = None,
                items = NonEmptyList.fromListUnsafe(batch).map(id => RankItem(id))
              )
              val request = Request[IO](
                method = Method.POST,
                uri = endpoint,
                entity = Entity.strict(ByteVector(json.asJson.noSpaces.getBytes()))
              )
              http.expect[RankResponse](request)
            })
            .sequence
        })
        rankings <- IO(result.flatMap(_.items))
        _        <- info(s"Writing $outPath")
        _        <- IO(File(outPath).writeText(rankings.map(r => s"${r.item.value},${r.score}").mkString("\n")))
        _        <- info(s"done")
      } yield {
        ExitCode.Success
      }
    case _ => IO.raiseError(new Exception("usage: sst <path to events> <out file> <endpoint>"))
  }

  def makeClient(): Resource[IO, Client[IO]] = {
    implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]
    EmberClientBuilder
      .default[IO]
      .withTimeout(10.second)
      .build
  }
}
