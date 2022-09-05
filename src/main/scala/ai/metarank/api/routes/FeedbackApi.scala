package ai.metarank.api.routes

import ai.metarank.FeatureMapping
import ai.metarank.api.JsonChunk
import ai.metarank.api.routes.FeedbackApi.FeedbackResponse
import ai.metarank.config.CoreConfig
import ai.metarank.flow.MetarankFlow
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankingEvent, UserEvent}
import ai.metarank.model.{Event, Field}
import ai.metarank.source.format.JsonFormat
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.Chunk
import org.http4s.dsl.io._
import org.http4s.{Entity, HttpRoutes, Response, Status}
import cats.implicits._
import io.circe.Codec
import io.circe.generic.semiauto._

case class FeedbackApi(store: Persistence, mapping: FeatureMapping, conf: CoreConfig) extends Logging {
  val routes = HttpRoutes.of[IO] {
    case post @ POST -> Root / "feedback" => {
      for {
        stream <- IO(post.entity.body.through(JsonFormat.parse).chunkN(1024).evalTap(logEvents))
        result <- MetarankFlow.process(store, stream.unchunks, mapping, conf)
      } yield {
        Response(
          status = Status.Ok,
          entity = Entity.strict(JsonChunk(FeedbackResponse("ok", result.events, result.updates, result.tookMillis)))
        )
      }
    }
  }

  def logEvents(events: Chunk[Event]): IO[Unit] = {
    if (events.size > 5) {
      IO {
        val items    = events.collect { case x: ItemEvent => x }.size
        val users    = events.collect { case x: UserEvent => x }.size
        val ints     = events.collect { case x: InteractionEvent => x }.size
        val rankings = events.collect { case x: RankingEvent => x }.size
        logger.info(s"batch: items=$items users=$users ints=$ints rank=$rankings")
      }
    } else {
      events.map(logEvent).sequence.void
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

object FeedbackApi {
  case class FeedbackResponse(status: String, accepted: Long, updated: Long, tookMillis: Long)
  implicit val feedbackResponseCodec: Codec[FeedbackResponse] = deriveCodec[FeedbackResponse]
}
