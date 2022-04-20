package ai.metarank.mode.inference.api

import ai.metarank.model.Event
import ai.metarank.source.LocalDirSource.LocalDirWriter
import ai.metarank.util.Logging
import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s._
import org.http4s.dsl.io._
import io.circe.syntax._
import org.http4s.circe._
import cats.implicits._

case class FeedbackApi(writer: LocalDirWriter) extends Logging {
  val routes = HttpRoutes.of[IO] { case post @ POST -> Root / "feedback" =>
    for {
      events <- post.as[Event].map(e => List(e)).handleErrorWith(_ => post.as[List[Event]])
      _      <- IO { logger.info(s"received event $events") }
      _      <- events.map(writer.write).sequence
      ok     <- Ok("")
    } yield {
      ok
    }
  }
  implicit val eventDecoder: EntityDecoder[IO, Event]           = jsonOf
  implicit val eventListDecoder: EntityDecoder[IO, List[Event]] = jsonOf
}
