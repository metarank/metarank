package ai.metarank.main.command.autofeature

import ai.metarank.model.Event.InteractionEvent

case class InteractionStat(types: Map[String, Int] = Map.empty) {
  def refresh(event: InteractionEvent) =
    copy(types = types.updatedWith(event.`type`)(countOption => Some(countOption.getOrElse(0) + 1)))
}
