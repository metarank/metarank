package ai.metarank.model

import io.findify.featury.model.{FeatureValue, Key}

case class EventState(event: Event, state: Map[Key, FeatureValue] = Map.empty)
