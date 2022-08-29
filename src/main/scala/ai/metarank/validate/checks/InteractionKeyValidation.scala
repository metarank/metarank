package ai.metarank.validate.checks

import ai.metarank.config.Config
import ai.metarank.model.Event
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.validate.EventValidation
import ai.metarank.validate.EventValidation.ValidationError

object InteractionKeyValidation extends EventValidation {
  case class InteractionKeyError(stale: Int) extends ValidationError
  case class State(stale: List[InteractionEvent] = Nil)

  override def name: String = "interaction-ranking join key check"

  override def validate(config: Config, events: List[Event]): List[EventValidation.ValidationError] = {
    val rankings = events.collect { case r: RankingEvent => r.id }.toSet
    val result = events
      .collect { case e: InteractionEvent => e }
      .foldLeft(State())((state, event) => {
        event.ranking match {
          case Some(ranking) => if (rankings.contains(ranking)) state else state.copy(stale = event +: state.stale)
          case None          => state
        }
      })

    result match {
      case State(Nil) =>
        logger.info(s"$name = PASS (${rankings.size} rankings, all interactions reference existing ones)")
        Nil
      case State(stale) =>
        logger.error(s"$name = FAIL (${stale.size} interaction are happened on non-existent rankings)")
        logger.error(s"Examples: ${stale.map(_.id).take(10)}")
        List(InteractionKeyError(stale.size))
    }
  }
}
