package ai.metarank.mode.validate

import ai.metarank.mode.validate.CheckResult._
import ai.metarank.util.Logging
import io.circe.{Json, JsonObject}
import io.circe.yaml.parser.parse

object ConfigValidator extends Logging {
  def check(file: String): CheckResult = {
    parse(file) match {
      case Left(value) => FailedCheck(s"yaml syntax error: ${value}")
      case Right(yaml) =>
        yaml.asObject match {
          case Some(obj) =>
            logger.info("config file is a YAML object")
            checkNonEmpty(obj, "interactions") match {
              case SuccessfulCheck => checkNonEmpty(obj, "features")
              case f: FailedCheck  => f
            }
          case None => FailedCheck("config file is not an YAML dictionary")
        }
    }
  }

  def checkNonEmpty(obj: JsonObject, section: String): CheckResult = {
    obj(section) match {
      case Some(s) =>
        logger.info(s"$section section exists")
        s.asArray match {
          case Some(list) if list.isEmpty => FailedCheck(s"'$section' section is empty")
          case Some(_) =>
            logger.info(s"$section section is not empty")
            SuccessfulCheck
          case None => FailedCheck(s"'$section' section is not a list")
        }
      case None => FailedCheck(s"'$section' section is missing in config")
    }
  }

}
