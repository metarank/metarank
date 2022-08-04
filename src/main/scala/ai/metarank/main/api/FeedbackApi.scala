package ai.metarank.main.api

import ai.metarank.model.Event
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.std.Queue
import io.circe.parser.decode
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, Response, Status}
import io.circe.syntax._
import cats.implicits._

case class FeedbackApi(queue: Queue[IO, Event]) extends Logging {
  val routes = HttpRoutes.of[IO] {
    case GET -> Root / "feedback" =>
      for {
        eventOption <- queue.tryTake
        response <- eventOption match {
          case None        => NoContent()
          case Some(event) => Ok(event.asJson.noSpaces)
        }
      } yield {
        response
      }
    case post @ POST -> Root / "feedback" =>
      for {
        eventsJson <- post.as[String]
        events     <- IO.fromEither(decodeEvent(eventsJson))
        _          <- IO { logger.info(s"received event $events") }
        accepted   <- events.map(e => queue.tryOffer(e)).sequence
      } yield {
        if (accepted.forall(_ == true)) {
          Response(status = Status.Ok)
        } else {
          logger.warn(s"cannot enqueue ${accepted.count(_ == false)} feedback events")
          Response(status = Status.InternalServerError)
        }
      }
  }

  def decodeEvent(json: String) = decode[Event](json) match {
    case Left(value)  => decode[List[Event]](json)
    case Right(value) => Right(List(value))
  }
}
