package ai.metarank.ml

import ai.metarank.FeatureMapping
import ai.metarank.flow.ClickthroughQuery
import ai.metarank.fstore.{FeatureValueLoader, Persistence}
import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.api.routes.RankApi.{ModelError, RankResponse}
import ai.metarank.model.Event.RankingEvent
import ai.metarank.api.routes.RankApi.RankResponse.{ItemScoreValues, StateValues}
import ai.metarank.config.ModelConfig
import ai.metarank.feature.BaseFeature.ValueMode
import ai.metarank.ml.Model.RankModel
import ai.metarank.ml.Predictor.{RankPredictor, RecommendPredictor}
import ai.metarank.model.{FeatureValue, ItemValue, Key}
import ai.metarank.ml.Ranker.QueryValues
import ai.metarank.ml.rank.LambdaMARTRanker.{LambdaMARTModel, LambdaMARTPredictor}
import ai.metarank.ml.rank.NoopRanker.{NoopModel, NoopPredictor}
import ai.metarank.ml.rank.QueryRequest
import ai.metarank.ml.rank.ShuffleRanker.{ShuffleModel, ShufflePredictor}
import ai.metarank.util.{KendallCorrelation, Logging}
import cats.data.NonEmptyList
import cats.effect.IO
import io.github.metarank.ltrlib.model.{DatasetDescriptor, Query}

case class Ranker(mapping: FeatureMapping, store: Persistence) extends Logging {
  def rerank(request: RankingEvent, modelName: String, explain: Boolean): IO[RankResponse] =
    for {
      start <- IO { System.currentTimeMillis() }
      predictor <- mapping.models.get(modelName) match {
        case Some(existing: RankPredictor[_, _]) => IO.pure(existing)
        case Some(existing: RecommendPredictor[_, _]) =>
          IO.raiseError(ModelError(s"cannot rerank over recommender model $modelName"))
        case None => IO.raiseError(ModelError(s"model $modelName is not configured"))
      }
      model <- loadModel(predictor, modelName)
      queryValues <- predictor match {
        case LambdaMARTPredictor(name, config, desc) => makeQuery(request, desc)
        case _                                       => makeQuery(request, DatasetDescriptor(Map.empty, Nil, 0))
      }
      stateTook <- IO { System.currentTimeMillis() }
      scores    <- model.predict(QueryRequest(request, queryValues.query))
      result <- explain match {
        case true =>
          IO {
            queryValues.values
              .zip(scores.items)
              .map(x => ItemScoreValues(x._1.id, x._2.score, Some(x._1.values)))
              .sortBy(-_.score)
          }
        case false =>
          IO {
            queryValues.values
              .zip(scores.items)
              .map(x => ItemScoreValues(x._1.id, x._2.score, None))
              .sortBy(-_.score)
          }
      }
      _ <- IO {
        val items   = result.map(is => s"${is.item.value}=${String.format("%.2f", is.score)}").toList.mkString(",")
        val total   = System.currentTimeMillis() - start
        val kendall = KendallCorrelation(request.items.map(_.id).toList, result.map(_.item).toList)
        logger.info(
          s"response: krr=$kendall user=${request.user.getOrElse("")} items=$items state=${stateTook - start}ms, total=${total}ms"
        )
      }

    } yield {
      RankResponse(state = Option.when(explain)(StateValues(queryValues.state.values.toList)), items = result.toList)
    }

  def loadModel(pred: Predictor[_, _, _], name: String): IO[RankModel] = pred match {
    case lm: LambdaMARTPredictor =>
      store.models.get(ModelName(name), lm).flatMap {
        case Some(s: LambdaMARTModel) => IO.pure(s)
        case Some(other)              => IO.raiseError(ModelError(s"model $name has wrong type $other"))
        case None                     => IO.raiseError(ModelError(s"model scorer $name is not yet trained"))
      }
    case p: NoopPredictor    => IO.pure(NoopModel(pred.name, p.config))
    case p: ShufflePredictor => IO.pure(ShuffleModel(p.name, p.config))
    case other               => IO.raiseError(ModelError(s"model type $other not supported"))
  }

  def makeQuery(request: RankingEvent, ds: DatasetDescriptor) = for {
    state             <- FeatureValueLoader.fromStateBackend(mapping, request, store.values)
    itemFeatureValues <- IO.fromEither(ItemValue.fromState(request, state, mapping, ValueMode.OnlineInference))
    query             <- IO { ClickthroughQuery(itemFeatureValues.toList, request.id.value, ds) }
    _                 <- IO { logger.info(s"generated query ${query.group} size=${query.columns}x${query.rows}") }
  } yield {
    QueryValues(query, itemFeatureValues, state)
  }
}

object Ranker {
  case class QueryValues(query: Query, values: NonEmptyList[ItemValue], state: Map[Key, FeatureValue])
}
