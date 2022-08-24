package ai.metarank.rank

import ai.metarank.FeatureMapping
import ai.metarank.flow.ClickthroughQuery
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.main.api.RankApi.ModelError
import ai.metarank.model.Event.RankingEvent
import ai.metarank.model.RankResponse.{ItemScore, StateValues}
import ai.metarank.model.{FeatureValue, ItemValue, Key, RankResponse}
import ai.metarank.rank.Ranker.QueryValues
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
      scorer <- store.models.get(ModelName(modelName)).flatMap {
        case Some(s) => IO.pure(s)
        case None    => IO.raiseError(ModelError(s"model scorer $modelName is not yet trained"))
      }
      scores <- IO { scorer.score(queryValues.query) }
      result <- explain match {
        case true  => IO { queryValues.values.zip(scores).map(x => ItemScore(x._1.id, x._2, x._1.values)) }
        case false => IO { queryValues.values.zip(scores).map(x => ItemScore(x._1.id, x._2, Nil)) }
      }
      _ <- IO {
        val items = result.map(is => s"${is.item.value}=${String.format("%.2f", is.score)}").mkString(",")
        val total = System.currentTimeMillis() - start
        logger.info(s"response: user=${request.user.value} items=$items state=${stateTook - start}ms, total=${total}ms")
      }

    } yield {
      RankResponse(state = StateValues(queryValues.state.values.toList), items = result.sortBy(-_.score))
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
