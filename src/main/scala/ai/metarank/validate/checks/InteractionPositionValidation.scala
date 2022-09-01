package ai.metarank.validate.checks

import ai.metarank.config.Config
import ai.metarank.model.Event
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.validate.EventValidation
import ai.metarank.validate.EventValidation.ValidationError

object InteractionPositionValidation extends EventValidation {
  case class InteractionPositionError(dist: Vector[Int], nonExistentItems: List[InteractionEvent])
      extends ValidationError
  case class State(positions: Vector[Int], nonExistentItems: List[InteractionEvent] = Nil)
  override def name: String = "interaction positions check"

  override def validate(config: Config, events: List[Event]): List[EventValidation.ValidationError] = {
    val rankings    = events.collect { case r: RankingEvent => r.id -> r.items.map(_.id).toList.toVector }.toMap
    val maxPosition = rankings.values.map(_.size).max
    val initial     = State(Vector.fill(maxPosition)(0))
    val result = events
      .collect { case e: InteractionEvent => e }
      .foldLeft(initial)((state, e) => {
        e.ranking match {
          case Some(ranking) =>
            rankings.get(ranking) match {
              case Some(positions) =>
                val pos = positions.indexOf(e.item)
                if (pos >= 0) {
                  state.copy(positions = state.positions.updated(pos, state.positions(pos) + 1))
                } else {
                  state.copy(nonExistentItems = e +: state.nonExistentItems)
                }
              case None => state
            }
          case None => state
        }
      })
    result match {
      case State(clicks, Nil) if evenDistribution(clicks) =>
        logger.info(s"$name = PASS (int distribution: ${clicks.mkString("[", ",", "]")}")
        Nil
      case State(clicks, Nil) =>
        logger.warn(s"$name = FAIL (all interactions always happening on the same position)")
        logger.warn(s"Histogram: ${clicks}")
        List(InteractionPositionError(clicks, Nil))
      case State(clicks, nonEmpty) =>
        logger.warn(s"$name = FAIL (${nonEmpty.size} interactions happened on items missing in ranking)")
        logger.warn(s"Examples: ${nonEmpty.take(10)}")
        List(InteractionPositionError(clicks, nonEmpty))
    }
  }

  def evenDistribution(clicks: Vector[Int]): Boolean = {
    val nonZero = clicks.count(_ != 0)
    nonZero > 1
  }
}
