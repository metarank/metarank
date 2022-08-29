package ai.metarank.validate.checks

import ai.metarank.config.Config
import ai.metarank.model.Event
import ai.metarank.model.Event.{InteractionEvent, ItemEvent}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.validate.EventValidation
import ai.metarank.validate.EventValidation.ValidationError

object InteractionMetadataValidation extends EventValidation {
  case class InteractionWithoutMetadataError(items: List[ItemId]) extends ValidationError
  case class State(missing: List[InteractionEvent] = Nil, ooo: List[InteractionEvent] = Nil)

  override def name: String = "Interaction metadata check"

  override def validate(config: Config, events: List[Event]): List[EventValidation.ValidationError] = {
    val itemTimestamps = events
      .collect { case event: ItemEvent => event }
      .groupBy(_.item)
      .map { case (item, events) => item -> events.map(_.timestamp).minBy(_.ts) }

    val result = events.foldLeft(State())((state, event) =>
      event match {
        case e @ InteractionEvent(_, item, ts, _, _, _, _, _) =>
          itemTimestamps.get(item) match {
            case Some(first) => if (ts.isBeforeOrEquals(first)) state.copy(ooo = e +: state.ooo) else state
            case None        => state.copy(missing = e +: state.missing)
          }
        case _ => state
      }
    )

    result match {
      case State(Nil, Nil) =>
        logger.info(s"$name: PASS")
        Nil
      case State(missing, Nil) =>
        logger.error(s"$name: FAIL (${missing.size} interaction happened on never seen items)")
        logger.error(s"examples: ${missing.map(_.id.value).take(10)}")
        List(InteractionWithoutMetadataError(missing.map(_.item)))
      case State(_, ooo) =>
        logger.error(s"$name: FAIL (${ooo.size} interaction happened before we seen item metadata event)")
        logger.error(s"examples: ${ooo.map(_.id.value).take(10)}")
        List(InteractionWithoutMetadataError(ooo.map(_.item)))

    }
  }

}
