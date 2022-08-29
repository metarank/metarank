package ai.metarank.validate.checks

import ai.metarank.config.Config
import ai.metarank.model.Event
import ai.metarank.util.Logging
import ai.metarank.validate.EventValidation
import ai.metarank.validate.EventValidation.ValidationError

object EventOrderValidation extends EventValidation with Logging {
  val name = "Event ordering check"
  case class OrderState(ts: Long = 0L, oooCount: Int = 0, firstOoo: Option[Event] = None)
  override def validate(config: Config, events: List[Event]): List[EventValidation.ValidationError] = {
    val result = events.foldLeft(OrderState())((state, event) =>
      if (event.timestamp.ts >= state.ts) state.copy(ts = event.timestamp.ts)
      else {
        state.copy(oooCount = state.oooCount + 1, firstOoo = Some(state.firstOoo.getOrElse(event)))
      }
    )
    if (result.oooCount > 0) {
      logger.error(s"$name = FAIL (${result.oooCount}/${events.size} events are out of order)")
      logger.error(s"last known ts=${result.ts}, but got event ${result.firstOoo}")
      List(EventOrderError(result))
    } else {
      logger.info(s"$name = PASS (${events.size} events sorted by timestamp)")
      Nil
    }
  }

  case class EventOrderError(state: OrderState) extends ValidationError
}
