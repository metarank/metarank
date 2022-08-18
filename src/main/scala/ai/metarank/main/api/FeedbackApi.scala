package ai.metarank.main.api

import ai.metarank.model.{Event, Field}
import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankingEvent, UserEvent}
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.std.Queue
import io.circe.parser.decode
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, Response, Status}
import cats.implicits._

case class FeedbackApi(queue: Queue[IO, Option[Event]]) extends Logging {
  val routes = HttpRoutes.of[IO] { case post @ POST -> Root / "feedback" =>
    for {
      eventsJson <- post.as[String]
      events     <- IO.fromEither(decodeEvent(eventsJson))
      _          <- IO { logEvent(events) }
      accepted   <- events.map(e => queue.tryOffer(Some(e))).sequence
    } yield {
      if (accepted.forall(_ == true)) {
        Response(status = Status.Ok)
      } else {
        logger.warn(s"cannot enqueue ${accepted.count(_ == false)} feedback events")
        Response(status = Status.InternalServerError)
      }
    }
  }

  def logEvent(events: List[Event]): Unit = {

    events.foreach {
      case e: ItemEvent =>
        logger.info(s"item: item=${e.item.value} fields=${Field.toString(e.fields)}")
      case e: UserEvent =>
        logger.info(s"user: user=${e.user.value} fields=${Field.toString(e.fields)}")
      case e: RankingEvent =>
        val items = e.items.map(_.id.value).toList.mkString("[", ",", "]")
        logger.info(s"ranking: user=${e.user.value} items=$items fields=${Field.toString(e.fields)}")
      case e: InteractionEvent =>
        logger.info(
          s"interaction: user=${e.user.value} item=${e.item.value} type=${e.`type`} fields=${Field.toString(e.fields)}"
        )
    }
  }

  def decodeEvent(json: String): Either[Throwable, List[Event]] = decode[Event](json) match {
    case Left(value) =>
      decode[List[Event]](json) match {
        case Left(value)  => json.split('\n').toList.map(e => decode[Event](e)).sequence
        case Right(value) => Right(value)
      }
    case Right(value) => Right(List(value))
  }
}
