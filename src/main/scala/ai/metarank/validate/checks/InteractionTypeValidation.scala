package ai.metarank.validate.checks

import ai.metarank.config.{Config, ModelConfig}
import ai.metarank.model.Event
import ai.metarank.model.Event.InteractionEvent
import ai.metarank.validate.EventValidation
import ai.metarank.validate.EventValidation.ValidationError

object InteractionTypeValidation extends EventValidation {
  case class InteractionTypeError(wrong: Int, types: Set[String]) extends ValidationError
  case class State(wrong: Int = 0, types: Set[String] = Set.empty)
  override def name: String = "interaction type check"

  override def validate(config: Config, events: List[Event]): List[EventValidation.ValidationError] = {
    val types = config.models.values
      .collect { case ModelConfig.LambdaMARTConfig(_, _, weights, _) =>
        weights.keys
      }
      .flatten
      .toSet
    val interactions = events.collect { case e: InteractionEvent => e }
    val result = interactions
      .foldLeft(State())((state, event) => {
        if (types.contains(event.`type`)) state
        else state.copy(wrong = state.wrong + 1, types = state.types ++ Set(event.`type`))
      })
    if (result.wrong == 0) {
      logger.info(s"$name = PASS (${interactions.size} interactions have known types: ${types})")
      Nil
    } else {
      logger.warn(s"$name = FAIL (${result.wrong} interactions have unknown types: ${result.types})")
      List(InteractionTypeError(result.wrong, result.types))
    }
  }
}
