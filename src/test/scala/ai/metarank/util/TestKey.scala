package ai.metarank.util

import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.Scope.{GlobalScope, ItemFieldScope, ItemScope, SessionScope, UserFieldScope, UserScope}
import ai.metarank.model.{Key, ScopeType}

object TestKey {
  def apply(c: FeatureConfig, id: String) = {
    c.scope match {
      case ScopeType.GlobalScopeType           => Key(GlobalScope, c.name)
      case ScopeType.ItemScopeType             => Key(ItemScope(ItemId(id)), c.name)
      case ScopeType.UserScopeType             => Key(UserScope(UserId(id)), c.name)
      case ScopeType.SessionScopeType          => Key(SessionScope(SessionId(id)), c.name)
      case ScopeType.UserFieldScopeType(field) => Key(UserFieldScope(UserId(id), field), c.name)
      case ScopeType.ItemFieldScopeType(field) => Key(ItemFieldScope(ItemId(id), field), c.name)
    }
  }
}
