package ai.metarank.main.api

import ai.metarank.FeatureMapping
import ai.metarank.flow.ClickthroughQuery
import ai.metarank.fstore.Persistence
import ai.metarank.main.RankResponse
import RankResponse.{ItemScore, StateValues}
import ai.metarank.model.Event.RankingEvent
import ai.metarank.rank.Model.Scorer
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

case class RankApi(
    mapping: FeatureMapping,
    store: Persistence,
    scorers: Map[String, Scorer]
) extends Logging {
  import RankApi._
  import ai.metarank.model.Event.EventCodecs._

  val routes = HttpRoutes.of[IO] { case post @ POST -> Root / "rank" / model :? ExplainParamDecoder(explain) =>
    for {
      requestJson <- post.as[String]
      request     <- IO.fromEither(decode[RankingEvent](requestJson))
      response    <- rerank(request, model, explain.getOrElse(false))
    } yield {
      Response[IO](
        Status.Ok,
        headers = Headers(`Content-Type`(MediaType.application.json)),
        entity = Entity.strict(Chunk.array(response.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)))
      )
    }
  }

  def rerank(request: RankingEvent, model: String, explain: Boolean): IO[RankResponse] = for {
    start     <- IO { System.currentTimeMillis() }
    keys      <- IO { mapping.stateReadKeys(request) }
    state     <- store.state.get(keys).map(_.values.toList)
    stateTook <- IO { System.currentTimeMillis() }
    ranker <- mapping.models.get(model) match {
      case Some(existing) => IO.pure(existing)
      case None           => IO.raiseError(ModelError(s"model $model is not configured"))
    }
    items <- IO { ranker.featureValues(request, state) }
    query <- IO { ClickthroughQuery(items, request.id.value, ranker.datasetDescriptor) }
    scorer <- scorers.get(model) match {
      case Some(existing) => IO.pure(existing)
      case None           => IO.raiseError(ModelError(s"model $model is not configured"))
    }
    scores <- IO { scorer.score(query) }
    result <- explain match {
      case true  => IO { items.zip(scores).map(x => ItemScore(x._1.id, x._2, x._1.values)) }
      case false => IO { items.zip(scores).map(x => ItemScore(x._1.id, x._2, Nil)) }
    }
    _ <- IO {
      logger.info(
        s"processing time: state loading ${stateTook - start}ms, total ${System.currentTimeMillis() - start}ms"
      )
    }

  } yield {
    RankResponse(state = StateValues(state), items = result.sortBy(-_.score))
  }

}

object RankApi {

  object ExplainParamDecoder             extends OptionalQueryParamDecoderMatcher[Boolean]("explain")
  case class StateReadError(msg: String) extends Exception(msg)
  case class ModelError(msg: String)     extends Exception(msg)
}
