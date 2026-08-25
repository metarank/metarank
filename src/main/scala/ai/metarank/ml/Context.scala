package ai.metarank.ml

import ai.metarank.model.Identifier.UserId

trait Context {
  def user: Option[UserId]
}
