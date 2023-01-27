package ai.metarank.ml.rank

import ai.metarank.ml.Context
import ai.metarank.model.Event.RankItem
import ai.metarank.model.{EventId, Field}
import ai.metarank.model.Identifier.{SessionId, UserId}

case class RankRequest(
    user: Option[UserId] = None,
    session: Option[SessionId] = None,
    fields: List[Field] = Nil,
    items: List[RankItem]
) extends Context
