package ai.metarank.validate

import ai.metarank.config.Config
import ai.metarank.model.Event
import ai.metarank.util.Logging
import ai.metarank.validate.EventValidation.ValidationError

trait EventValidation extends Logging {
  def name: String
  def validate(config: Config, events: List[Event]): List[ValidationError]
}

object EventValidation {
  trait ValidationError
}
