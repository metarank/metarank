package ai.metarank.validate.checks

import ai.metarank.config.Config
import ai.metarank.model.Event
import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankingEvent}
import ai.metarank.validate.EventValidation
import ai.metarank.validate.EventValidation.ValidationError

object EventTypesValidation extends EventValidation {
  case class MissingEventTypeError(items: Int, rankings: Int, ints: Int) extends ValidationError
  override def name: String = "event types check"

  override def validate(config: Config, events: List[Event]): List[EventValidation.ValidationError] = {
    val itemEvents        = events.collect { case e: ItemEvent => e }
    val rankingEvents     = events.collect { case e: RankingEvent => e }
    val interactionEvents = events.collect { case e: InteractionEvent => e }
    if (itemEvents.isEmpty) {
      logger.warn(s"$name = WARN (0 item metadata events found)")
      logger.warn("this may be OK if you're not using any per-item metadata in ranking at all")
      List(MissingEventTypeError(itemEvents.size, rankingEvents.size, interactionEvents.size))
    } else if (rankingEvents.isEmpty) {
      logger.error(s"$name = FAIL (0 ranking events found)")
      logger.warn("Metarank cannot train the ML model without ranking events")
      List(MissingEventTypeError(itemEvents.size, rankingEvents.size, interactionEvents.size))
    } else if (interactionEvents.isEmpty) {
      logger.error(s"$name = FAIL (0 interaction events found)")
      logger.warn("Metarank cannot train the ML model without interactions")
      List(MissingEventTypeError(itemEvents.size, rankingEvents.size, interactionEvents.size))
    } else {
      logger.info(
        s"$name = PASS (${itemEvents.size} item events, ${rankingEvents.size} rankings, ${interactionEvents.size} interactions)"
      )
      Nil
    }
  }

}
