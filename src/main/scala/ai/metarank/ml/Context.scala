package ai.metarank.ml

import ai.metarank.model.Identifier.{SessionId, UserId}

trait Context {
  def user: Option[UserId]
}
