package ai.metarank.mode.inference.api

import ai.metarank.model.Event
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.std.Queue
import org.http4s._
import io.circe.syntax._
import org.http4s.circe._
import cats.implicits._
import org.http4s.dsl.io._

case class FeedbackApi(queue: Queue[IO, Event]) extends Logging {
  val routes = HttpRoutes.of[IO] {
    case GET -> Root / "feedback" =>
      for {
        eventOption <- queue.tryTake
        response <- eventOption match {
          case None        => NoContent()
          case Some(event) => Ok(event.asJson)
        }
      } yield {
        response
      }
    case post @ POST -> Root / "feedback" =>
      for {
        events   <- post.as[Event].map(e => List(e)).handleErrorWith(_ => post.as[List[Event]])
        _        <- IO { logger.info(s"received event $events") }
        accepted <- events.map(e => queue.tryOffer(e)).sequence
      } yield {
        if (accepted.forall(_ == true)) {
          Response(status = Status.Ok)
        } else {
          logger.warn(s"cannot enqueue ${accepted.count(_ == false)} feedback events")
          Response(status = Status.InternalServerError)
        }
      }
  }
  implicit val eventDecoder: EntityDecoder[IO, Event]           = jsonOf
  implicit val eventEncoder: EntityEncoder[IO, Event]           = jsonEncoderOf
  implicit val eventListDecoder: EntityDecoder[IO, List[Event]] = jsonOf
}
