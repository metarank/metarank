package ai.metarank.model

import ai.metarank.model.Identifier.UserId
import io.github.metarank.ltrlib.model.Query

case class QueryMetadata(query: Query, ts: Timestamp, user: UserId)
