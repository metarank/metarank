package ai.metarank.feature.ua

import ai.metarank.feature.UserAgentFeature.UAField
import ua_parser.Client

object BrowserField extends UAField {
  val name = "browser"
  val browsers = Map(
    "Mobile Safari"     -> "safari",
    "Chrome"            -> "chrome",
    "Chrome Mobile"     -> "chrome",
    "Instagram"         -> "other",
    "Facebook"          -> "other",
    "Safari"            -> "safari",
    "Other"             -> "other",
    "Samsung Internet"  -> "other",
    "Edge"              -> "edge",
    "Chrome Mobile iOS" -> "chrome",
    "Firefox"           -> "firefox",
    "Opera"             -> "opera",
    "Firefox Mobile"    -> "firefox",
    "IE"                -> "ie"
  )
  override def value(client: Client): Option[String] = {
    client.userAgent.family match {
      case ""      => None
      case "Other" => None
      case family  => browsers.get(family)
    }
  }

  override lazy val possibleValues: List[String] = browsers.values.toList.distinct
}
