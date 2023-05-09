package ai.metarank.api.routes

import ai.metarank.api.JsonChunk
import ai.metarank.api.routes.RankApi.RankResponse.{ItemScoreValues, StateValues}
import ai.metarank.model.Event.RankingEvent
import ai.metarank.model.Field
import ai.metarank.ml.Ranker
import ai.metarank.util.Logging
import cats.effect._
import io.circe.parser._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import ai.metarank.model.Identifier._
import ai.metarank.model.ScopeType.{GlobalScopeType, ItemScopeType, SessionScopeType, UserScopeType}
import ai.metarank.model.{FeatureValue, MValue}
import ai.metarank.util.analytics.Metrics
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class RankApi(ranker: Ranker) extends Logging {
  import RankApi._
  import ai.metarank.model.Event.EventCodecs._

  val routes = HttpRoutes.of[IO] { case post @ POST -> Root / "rank" / model :? ExplainParamDecoder(explain) =>
    for {
      _           <- IO(Metrics.requests.labels(model).inc())
      start       <- IO(Metrics.requestLatency.labels(model).startTimer())
      requestJson <- post.as[String]
      request     <- IO.fromEither(decode[RankingEvent](requestJson))
      _           <- IO { logRequest(model, request) }
      response    <- ranker.rerank(request, model, explain.getOrElse(false))
      _           <- IO(start.observeDuration())
    } yield {
      Response[IO](
        Status.Ok,
        headers = Headers(`Content-Type`(MediaType.application.json)),
        entity = Entity.strict(JsonChunk(response))
      )
    }
  }

  def logRequest(model: String, r: RankingEvent) = {
    val items = r.items.map(_.id.value).toList.mkString("[", ",", "]")
    logger.info(
      s"request: /rank/$model user=${r.user.getOrElse("")} session=${r.session.map(_.value)} items=$items fields=${Field
          .toString(r.fields)}"
    )
  }

}

object RankApi {

  object ExplainParamDecoder         extends OptionalQueryParamDecoderMatcher[Boolean]("explain")
  case class ModelError(msg: String) extends Exception(msg)

  case class RankResponse(state: Option[StateValues], items: List[ItemScoreValues], took: Long)

  object RankResponse {
    case class StateValues(
        session: List[FeatureValue],
        user: List[FeatureValue],
        global: List[FeatureValue],
        item: List[FeatureValue]
    )

    object StateValues {
      def apply(values: List[FeatureValue]) = {
        new StateValues(
          session = values.filter(_.key.scope.getType == SessionScopeType),
          user = values.filter(_.key.scope.getType == UserScopeType),
          global = values.filter(_.key.scope.getType == GlobalScopeType),
          item = values.filter(_.key.scope.getType == ItemScopeType)
        )
      }
    }

    case class ItemScoreValues(item: ItemId, score: Double, features: Option[List[MValue]])

    implicit val itemScoreCodec: Codec[ItemScoreValues] = deriveCodec
    implicit val stateValuesCodec: Codec[StateValues]   = deriveCodec
    implicit val rankResponseCodec: Codec[RankResponse] = deriveCodec
  }

}
