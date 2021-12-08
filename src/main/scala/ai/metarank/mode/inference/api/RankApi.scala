package ai.metarank.mode.inference.api

import ai.metarank.FeatureMapping
import ai.metarank.flow.ClickthroughQuery
import ai.metarank.mode.inference.api.RankApi.ItemScore
import ai.metarank.mode.inference.ranking.RankScorer
import ai.metarank.model.Event.RankingEvent
import ai.metarank.model.{FeatureScope, ItemId, MValue}
import cats.effect.IO
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.HttpRoutes
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import io.circe.syntax._
import io.findify.featury.model.api.ReadRequest
import io.findify.featury.values.FeatureStore
import org.http4s.circe._

case class RankApi(mapping: FeatureMapping, store: FeatureStore, scorer: RankScorer) {
  import RankApi._

  val routes = HttpRoutes.of[IO] { case post @ POST -> Root / "rank" :? ExplainParamDecoder(explain) =>
    for {
      request  <- post.as[RankingEvent]
      response <- rerank(request, explain.getOrElse(true))
      ok       <- Ok(response.asJson)
    } yield {
      ok
    }
  }

  def rerank(request: RankingEvent, explain: Boolean) = for {
    keys   <- IO { mapping.keys(request) }
    state  <- store.read(ReadRequest(keys.toList))
    items  <- IO { mapping.map(request, state.features) }
    query  <- IO { ClickthroughQuery(items, request.id.value, mapping.datasetDescriptor) }
    scores <- IO { scorer.score(query) }
    result <- explain match {
      case true  => IO { items.zip(scores).map(x => ItemScore(x._1.id, x._2, x._1.values)) }
      case false => IO { items.zip(scores).map(x => ItemScore(x._1.id, x._2, Nil)) }
    }
  } yield {
    result.sortBy(-_.score)
  }
}

object RankApi {
  case class ItemScore(item: ItemId, score: Double, features: List[MValue])
  implicit val itemScoreCodec: Codec[ItemScore] = deriveCodec

  implicit val requestDecoder: EntityDecoder[IO, RankingEvent]      = jsonOf
  implicit val itemScoreEncoder: EntityEncoder[IO, List[ItemScore]] = jsonEncoderOf

  object ExplainParamDecoder extends OptionalQueryParamDecoderMatcher[Boolean]("explain")
}
