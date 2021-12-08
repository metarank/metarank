package ai.metarank.mode.inference.api

import ai.metarank.FeatureMapping
import ai.metarank.flow.ClickthroughQuery
import ai.metarank.mode.inference.api.RankApi.ItemScore
import ai.metarank.model.Event.RankingEvent
import ai.metarank.model.{FeatureScope, ItemId}
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
import io.github.metarank.ltrlib.booster.LightGBMBooster
import org.http4s.circe._

case class RankApi(mapping: FeatureMapping, store: FeatureStore, model: String) {
  import RankApi._
  val booster = LightGBMBooster(model)

  val routes = HttpRoutes.of[IO] { case post @ POST -> Root / "rank" =>
    for {
      request  <- post.as[RankingEvent]
      response <- rerank(request)
      ok       <- Ok(response.asJson)
    } yield {
      ok
    }
  }

  def rerank(request: RankingEvent) = for {
    keys  <- IO { mapping.keys(request) }
    state <- store.read(ReadRequest(keys.toList))
    items <- IO { mapping.map(request, state.features) }
    query <- IO { ClickthroughQuery(items, request.id.value, mapping.datasetDescriptor) }
    values = Array(0.0) ++ query.values
    scores <- IO { booster.predictMat(values, query.rows, query.columns + 1) }
    result <- IO { items.zip(scores).map(x => ItemScore(x._1.id, x._2)) }
  } yield {
    result
  }
}

object RankApi {
  case class ItemScore(item: ItemId, score: Double)
  implicit val itemScoreCodec: Codec[ItemScore] = deriveCodec

  implicit val requestDecoder: EntityDecoder[IO, RankingEvent]      = jsonOf
  implicit val itemScoreEncoder: EntityEncoder[IO, List[ItemScore]] = jsonEncoderOf
}
