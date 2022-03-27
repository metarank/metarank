package ai.metarank.mode.inference.api

import ai.metarank.FeatureMapping
import ai.metarank.flow.ClickthroughQuery
import ai.metarank.mode.inference.{FeatureStoreResource, RankResponse}
import ai.metarank.mode.inference.RankResponse.{ItemScore, StateValues}
import ai.metarank.mode.inference.ranking.RankScorer
import ai.metarank.model.Event.RankingEvent
import ai.metarank.util.Logging
import cats.effect.{IO, Ref}
import org.http4s.HttpRoutes
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import io.circe.syntax._
import io.findify.featury.model.api.ReadRequest
import io.findify.featury.values.FeatureStore
import org.http4s.circe._

case class RankApi(
    mapping: FeatureMapping,
    storeResourceRef: Ref[IO, FeatureStoreResource],
    scorer: RankScorer
) extends Logging {
  import RankApi._

  val routes = HttpRoutes.of[IO] { case post @ POST -> Root / "rank" :? ExplainParamDecoder(explain) =>
    for {
      request  <- post.as[RankingEvent]
      response <- rerank(request, explain.getOrElse(false))
      ok       <- Ok(response.asJson)
    } yield {
      ok
    }
  }

  def rerank(request: RankingEvent, explain: Boolean): IO[RankResponse] = for {
    keys          <- IO { mapping.keys(request) }
    storeResource <- storeResourceRef.get
    store         <- storeResource.storeRef.get
    state <- store.read(ReadRequest(keys.toList)).handleErrorWith { case ex: Throwable =>
      for {
        _           <- IO { logger.warn("error from store, reconnecting", ex) }
        reconnected <- storeResource.reconnect()
        _           <- storeResourceRef.set(reconnected)
        state2      <- store.read(ReadRequest(keys.toList))
      } yield {
        state2
      }
    }
    items  <- IO { mapping.map(request, state.features) }
    query  <- IO { ClickthroughQuery(items, request.id.value, mapping.datasetDescriptor) }
    scores <- IO { scorer.score(query) }
    result <- explain match {
      case true  => IO { items.zip(scores).map(x => ItemScore(x._1.id, x._2, x._1.values)) }
      case false => IO { items.zip(scores).map(x => ItemScore(x._1.id, x._2, Nil)) }
    }
  } yield {
    RankResponse(state = StateValues(state.features), items = result.sortBy(-_.score))
  }

}

object RankApi {

  implicit val requestDecoder: EntityDecoder[IO, RankingEvent]   = jsonOf
  implicit val itemScoreEncoder: EntityEncoder[IO, RankResponse] = jsonEncoderOf

  object ExplainParamDecoder extends OptionalQueryParamDecoderMatcher[Boolean]("explain")
}
