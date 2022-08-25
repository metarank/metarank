package ai.metarank.rank

import ai.metarank.FeatureMapping
import ai.metarank.flow.ClickthroughQuery
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.main.api.RankApi.ModelError
import ai.metarank.model.Event.RankingEvent
import ai.metarank.model.RankResponse.{ItemScore, StateValues}
import ai.metarank.model.{FeatureValue, ItemValue, Key, RankResponse}
import ai.metarank.rank.NoopModel.NoopScorer
import ai.metarank.rank.Ranker.QueryValues
import ai.metarank.rank.ShuffleModel.ShuffleScorer
import ai.metarank.util.KendallCorrelation
import cats.effect.IO
import io.github.metarank.ltrlib.input.CSVInputFormat.logger
import io.github.metarank.ltrlib.model.{DatasetDescriptor, Query}

case class Ranker(mapping: FeatureMapping, store: Persistence) {
  def rerank(request: RankingEvent, modelName: String, explain: Boolean): IO[RankResponse] =
    for {
      start <- IO { System.currentTimeMillis() }
      model <- mapping.models.get(modelName) match {
        case Some(existing) => IO.pure(existing)
        case None           => IO.raiseError(ModelError(s"model $modelName is not configured"))
      }
      queryValues <- makeQuery(request, model.datasetDescriptor)
      stateTook   <- IO { System.currentTimeMillis() }
      scorer      <- loadScorer(model, modelName)
      scores      <- IO { scorer.score(queryValues.query) }
      result <- explain match {
        case true =>
          IO { queryValues.values.zip(scores).map(x => ItemScore(x._1.id, x._2, Some(x._1.values))).sortBy(-_.score) }
        case false => IO { queryValues.values.zip(scores).map(x => ItemScore(x._1.id, x._2, None)).sortBy(-_.score) }
      }
      _ <- IO {
        val items   = result.map(is => s"${is.item.value}=${String.format("%.2f", is.score)}").mkString(",")
        val total   = System.currentTimeMillis() - start
        val kendall = KendallCorrelation(request.items.map(_.id).toList, result.map(_.item))
        logger.info(
          s"response: krr=$kendall user=${request.user.value} items=$items state=${stateTook - start}ms, total=${total}ms"
        )
      }

    } yield {
      RankResponse(state = Option.when(explain)(StateValues(queryValues.state.values.toList)), items = result)
    }

  def loadScorer(model: Model, name: String) = model match {
    case _: LambdaMARTModel =>
      store.models.get(ModelName(name)).flatMap {
        case Some(s) => IO.pure(s)
        case None    => IO.raiseError(ModelError(s"model scorer $name is not yet trained"))
      }
    case NoopModel(_)       => IO.pure(NoopScorer)
    case ShuffleModel(conf) => IO.pure(ShuffleScorer(conf.maxPositionChange))
  }

  def makeQuery(request: RankingEvent, ds: DatasetDescriptor) = for {
    keys              <- IO { mapping.stateReadKeys(request) }
    state             <- store.values.get(keys)
    itemFeatureValues <- IO { ItemValue.fromState(request, state, mapping) }
    query             <- IO { ClickthroughQuery(itemFeatureValues, request.id.value, ds) }
    _                 <- IO { logger.info(s"generated query ${query.group} size=${query.columns}x${query.rows}") }
  } yield {
    QueryValues(query, itemFeatureValues, state)
  }
}

object Ranker {
  case class QueryValues(query: Query, values: List[ItemValue], state: Map[Key, FeatureValue])
}
