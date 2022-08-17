package ai.metarank.main.api

import ai.metarank.FeatureMapping
import ai.metarank.flow.ClickthroughQuery
import ai.metarank.fstore.Persistence
import ai.metarank.model.RankResponse.{ItemScore, StateValues}
import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.model.Event.RankingEvent
import ai.metarank.model.{ItemValue, RankResponse}
import ai.metarank.rank.Model.Scorer
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

case class RankApi(
    mapping: FeatureMapping,
    store: Persistence,
    models: ModelCache
) extends Logging {
  import RankApi._
  import ai.metarank.model.Event.EventCodecs._

  val routes = HttpRoutes.of[IO] { case post @ POST -> Root / env / model / "_rank" :? ExplainParamDecoder(explain) =>
    for {
      requestJson <- post.as[String]
      request     <- IO.fromEither(decode[RankingEvent](requestJson))
      response    <- rerank(mapping, request, model, explain.getOrElse(false))
    } yield {
      Response[IO](
        Status.Ok,
        headers = Headers(`Content-Type`(MediaType.application.json)),
        entity = Entity.strict(Chunk.array(response.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)))
      )
    }
  }

  def rerank(mapping: FeatureMapping, request: RankingEvent, model: String, explain: Boolean): IO[RankResponse] = for {
    start     <- IO { System.currentTimeMillis() }
    keys      <- IO { mapping.stateReadKeys(request) }
    state     <- store.values.get(keys)
    stateTook <- IO { System.currentTimeMillis() }
    ranker <- mapping.models.get(model) match {
      case Some(existing) => IO.pure(existing)
      case None           => IO.raiseError(ModelError(s"model $model is not configured"))
    }
    itemFeatureValues <- IO { ItemValue.fromState(request, state, mapping) }
    query <- IO {
      ClickthroughQuery(itemFeatureValues, request.id.value, ranker.datasetDescriptor)
    }
    scorer <- models.get(model)
    scores <- IO { scorer.score(query) }
    result <- explain match {
      case true  => IO { itemFeatureValues.zip(scores).map(x => ItemScore(x._1.id, x._2, x._1.values)) }
      case false => IO { itemFeatureValues.zip(scores).map(x => ItemScore(x._1.id, x._2, Nil)) }
    }
    _ <- IO {
      logger.info(
        s"processing time: state loading ${stateTook - start}ms, total ${System.currentTimeMillis() - start}ms"
      )
    }

  } yield {
    RankResponse(state = StateValues(state.values.toList), items = result.sortBy(-_.score))
  }

}

object RankApi {

  object ExplainParamDecoder             extends OptionalQueryParamDecoderMatcher[Boolean]("explain")
  case class StateReadError(msg: String) extends Exception(msg)
  case class ModelError(msg: String)     extends Exception(msg)
}
