package ai.metarank.feature.ua

import ai.metarank.feature.UserAgentFeature.UAField
import ua_parser.Client

object OSField extends UAField {
  val OSNames = Map(
    "iOS"       -> "ios",
    "Android"   -> "android",
    "Windows"   -> "windows",
    "Mac OS X"  -> "osx",
    "Linux"     -> "linux",
    "Chrome OS" -> "chromeos"
  )
  override def value(client: Client): Option[String] = Option(client.os.family) match {
    case Some("")      => None
    case Some("Other") => None
    case None          => None
    case Some(family)  => OSNames.get(family)
  }

  override lazy val possibleValues: List[String] = OSNames.keys.toList
}
