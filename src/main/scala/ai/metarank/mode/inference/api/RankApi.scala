package ai.metarank.mode.inference.api

import ai.metarank.FeatureMapping
import ai.metarank.flow.ClickthroughQuery
import ai.metarank.mode.inference.{FeatureStoreResource, RankResponse}
import ai.metarank.mode.inference.RankResponse.{ItemScore, StateValues}
import ai.metarank.mode.inference.ranking.RankScorer
import ai.metarank.model.Event.RankingEvent
import ai.metarank.rank.Model.Scorer
import ai.metarank.util.Logging
import cats.effect._
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import io.circe.syntax._
import io.findify.featury.model.Key
import io.findify.featury.model.api.{ReadRequest, ReadResponse}
import org.http4s.Uri.Path.Segment
import org.http4s.circe._

import scala.concurrent.duration._

case class RankApi(
    mapping: FeatureMapping,
    storeResourceRef: Ref[IO, FeatureStoreResource],
    scorers: Map[String, Scorer]
) extends Logging {
  import RankApi._

  val routes = HttpRoutes.of[IO] { case post @ POST -> Root / "rank" / model :? ExplainParamDecoder(explain) =>
    for {
      request  <- post.as[RankingEvent]
      response <- rerank(request, model, explain.getOrElse(false))
      ok       <- Ok(response.asJson)
    } yield {
      ok
    }
  }

  def rerank(request: RankingEvent, model: String, explain: Boolean): IO[RankResponse] = for {
    start     <- IO { System.currentTimeMillis() }
    keys      <- IO { mapping.keys(request) }
    state     <- readState(keys.toList)
    stateTook <- IO { System.currentTimeMillis() }
    ranker <- mapping.models.get(model) match {
      case Some(existing) => IO.pure(existing)
      case None           => IO.raiseError(ModelError(s"model $model is not configured"))
    }
    items <- IO { ranker.featureValues(request, state.features) }
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
    RankResponse(state = StateValues(state.features), items = result.sortBy(-_.score))
  }

  val BATCH_SIZE        = 512
  val MAX_RECONNECTS    = 3
  val RECONNECT_TIMEOUT = 50.millis

  def readState(keys: List[Key], attempt: Int = 0): IO[ReadResponse] = for {
    storeResource <- storeResourceRef.get
    store         <- storeResource.storeRef.get
    batches = keys.grouped(BATCH_SIZE).toList
    state <- batches
      .map(batch => store.read(ReadRequest(batch)))
      .sequence
      .map(x => ReadResponse(x.flatMap(_.features)))
      .handleErrorWith {
        case ex: Throwable if attempt >= MAX_RECONNECTS =>
          logger.error(s"$MAX_RECONNECTS attempts to read ${keys.size} keys failed", ex)
          IO.raiseError(StateReadError(s"cannot read data from state"))
        case ex: Throwable =>
          for {
            _ <- IO { logger.warn(s"error from store, reconnecting (attempt $attempt of $MAX_RECONNECTS)", ex) }
            reconnected <- storeResource.reconnect()
            _           <- storeResourceRef.set(reconnected)
            _           <- IO.sleep(RECONNECT_TIMEOUT * attempt)
            state2      <- readState(keys, attempt + 1)
          } yield {
            state2
          }
      }
    _ <- IO {
      logger.info(
        s"request for ${keys.size} keys split to ${batches.size} batches, loaded ${state.features.size} values"
      )
    }
  } yield {
    state
  }

}

object RankApi {
  import ai.metarank.model.Event.EventCodecs._
  implicit val requestDecoder: EntityDecoder[IO, RankingEvent]   = jsonOf[IO, RankingEvent]
  implicit val itemScoreEncoder: EntityEncoder[IO, RankResponse] = jsonEncoderOf

  object ExplainParamDecoder             extends OptionalQueryParamDecoderMatcher[Boolean]("explain")
  case class StateReadError(msg: String) extends Exception(msg)
  case class ModelError(msg: String)     extends Exception(msg)
}
