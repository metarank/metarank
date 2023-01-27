package ai.metarank.ml.rank

import ai.metarank.ml.Context
import ai.metarank.model.Event.{RankItem, RankingEvent}
import ai.metarank.model.Identifier.{SessionId, UserId}
import ai.metarank.model.Timestamp
import cats.data.NonEmptyList
import io.github.metarank.ltrlib.model.Query

case class QueryRequest(
    user: Option[UserId],
    session: Option[SessionId],
    items: NonEmptyList[RankItem],
    ts: Timestamp,
    query: Query
) extends Context

object QueryRequest {
  def apply(req: RankingEvent, query: Query) = new QueryRequest(
    user = req.user,
    session = req.session,
    items = req.items,
    ts = req.timestamp,
    query = query
  )
}
