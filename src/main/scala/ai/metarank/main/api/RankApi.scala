package ai.metarank.main.api

import ai.metarank.FeatureMapping
import ai.metarank.flow.ClickthroughQuery
import ai.metarank.fstore.Persistence
import ai.metarank.model.RankResponse.{ItemScore, StateValues}
import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.model.Event.RankingEvent
import ai.metarank.model.{Field, ItemValue, RankResponse}
import ai.metarank.rank.Model.Scorer
import ai.metarank.rank.Ranker
import ai.metarank.source.ModelCache
import ai.metarank.util.Logging
import cats.effect._
import cats.implicits._
import fs2.Chunk
import io.circe.parser._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`

import java.nio.charset.StandardCharsets
import scala.concurrent.duration._

case class RankApi(ranker: Ranker) extends Logging {
  import RankApi._
  import ai.metarank.model.Event.EventCodecs._

  val routes = HttpRoutes.of[IO] { case post @ POST -> Root / "rank" / model :? ExplainParamDecoder(explain) =>
    for {
      requestJson <- post.as[String]
      request     <- IO.fromEither(decode[RankingEvent](requestJson))
      _           <- IO { logRequest(request) }
      response    <- ranker.rerank(request, model, explain.getOrElse(false))
    } yield {
      Response[IO](
        Status.Ok,
        headers = Headers(`Content-Type`(MediaType.application.json)),
        entity = Entity.strict(Chunk.array(response.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)))
      )
    }
  }

  def logRequest(r: RankingEvent) = {
    val items = r.items.map(_.id.value).toList.mkString("[", ",", "]")
    logger.info(
      s"request: user=${r.user.value} session=${r.session.map(_.value)} items=$items fields=${Field.toString(r.fields)}"
    )
  }

}

object RankApi {

  object ExplainParamDecoder             extends OptionalQueryParamDecoderMatcher[Boolean]("explain")
  case class StateReadError(msg: String) extends Exception(msg)
  case class ModelError(msg: String)     extends Exception(msg)
}
