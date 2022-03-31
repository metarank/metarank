package ai.metarank.mode.validate

sealed trait CheckResult

object CheckResult {
  case object SuccessfulCheck            extends CheckResult
  case class FailedCheck(reason: String) extends CheckResult
}
