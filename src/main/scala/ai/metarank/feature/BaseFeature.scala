package ai.metarank.feature

import ai.metarank.fstore.Persistence
import ai.metarank.model.Event.{InteractionEvent, ItemEvent, ItemRelevancy, RankingEvent, UserEvent}
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Scope.{GlobalScope, ItemScope, SessionScope, UserScope}
import ai.metarank.model.ScopeType.{GlobalScopeType, ItemScopeType, SessionScopeType, UserScopeType}
import ai.metarank.model.{Dimension, Event, FeatureSchema, FeatureValue, FieldName, Key, MValue, ScopeType, Write}
import cats.effect.IO

sealed trait BaseFeature {
  def dim: Dimension
  def schema: FeatureSchema
  def states: List[FeatureConfig]
  def writes(event: Event): IO[Iterable[Write]]

  def writeKey(event: Event, feature: FeatureConfig): Option[Key] = (feature.scope, event) match {
    case (GlobalScopeType, _)                    => Some(Key(GlobalScope, feature.name))
    case (UserScopeType, e: InteractionEvent)    => Some(Key(UserScope(e.user), feature.name))
    case (UserScopeType, e: UserEvent)           => Some(Key(UserScope(e.user), feature.name))
    case (SessionScopeType, e: InteractionEvent) => e.session.map(s => Key(SessionScope(s), feature.name))
    case (ItemScopeType, e: InteractionEvent)    => Some(Key(ItemScope(e.item), feature.name))
    case (ItemScopeType, e: ItemEvent)           => Some(Key(ItemScope(e.item), feature.name))
    case _                                       => None
  }

  def readKey(event: RankingEvent, conf: FeatureConfig, id: ItemId): Option[Key] = conf.scope match {
    case ScopeType.GlobalScopeType  => Some(Key(GlobalScope, conf.name))
    case ScopeType.ItemScopeType    => Some(Key(ItemScope(id), conf.name))
    case ScopeType.UserScopeType    => Some(Key(UserScope(event.user), conf.name))
    case ScopeType.SessionScopeType => event.session.map(s => Key(SessionScope(s), conf.name))
  }

  def valueKeys(event: RankingEvent): Iterable[Key]

  def valueKeys2(event: RankingEvent, features: Map[Key, FeatureValue]): Iterable[Key] = Nil

}

object BaseFeature {

  sealed trait ValueMode
  object ValueMode {
    case object OnlineInference extends ValueMode
    case object OfflineTraining extends ValueMode
  }

  trait ItemFeature extends BaseFeature {
    def value(
        request: Event.RankingEvent,
        features: Map[Key, FeatureValue],
        id: ItemRelevancy
    ): MValue

    def values(request: Event.RankingEvent, features: Map[Key, FeatureValue], mode: ValueMode): List[MValue] =
      request.items.toList.map(item => value(request, features, item))
  }

  trait RankingFeature extends BaseFeature {
    def value(
        request: Event.RankingEvent,
        features: Map[Key, FeatureValue]
    ): MValue
  }

}
