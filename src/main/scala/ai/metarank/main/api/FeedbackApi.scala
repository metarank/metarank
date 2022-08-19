package ai.metarank.main.api

import ai.metarank.FeatureMapping
import ai.metarank.flow.MetarankFlow
import ai.metarank.fstore.Persistence
import ai.metarank.model.{Event, Field}
import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankingEvent, UserEvent}
import ai.metarank.source.format.JsonFormat
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.text
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, Response, Status}

case class FeedbackApi(store: Persistence, mapping: FeatureMapping) extends Logging {
  val routes = HttpRoutes.of[IO] {
    case post @ POST -> Root / "feedback" => {
      val x = post.entity.body
        .through(text.utf8.decode)
        .through(text.lines)
        .compile
        .toList
        .unsafeRunSync()
      val br = 1

      for {
        stream <- IO(post.entity.body.through(JsonFormat.parse).evalTap(logEvent))
        _      <- MetarankFlow.process(store, stream, mapping)
      } yield {
        Response(status = Status.Ok)
      }
    }
  }

  def logEvent(event: Event): IO[Unit] = IO {
    event match {
      case e: ItemEvent =>
        logger.info(s"item: id=${e.id.value} item=${e.item.value} fields=${Field.toString(e.fields)}")
      case e: UserEvent =>
        logger.info(s"user: id=${e.id.value} user=${e.user.value} fields=${Field.toString(e.fields)}")
      case e: RankingEvent =>
        val items = e.items.map(_.id.value).toList.mkString("[", ",", "]")
        logger.info(s"ranking: id=${e.id.value} user=${e.user.value} items=$items fields=${Field.toString(e.fields)}")
      case e: InteractionEvent =>
        logger.info(
          s"interaction: id=${e.id.value} ranking=${e.ranking
              .map(_.value)} user=${e.user.value} item=${e.item.value} type=${e.`type`} fields=${Field.toString(e.fields)}"
        )
    }
  }
}
