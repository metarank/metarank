package ai.metarank.feature.ua

import ai.metarank.feature.UserAgentFeature.UAField
import ua_parser.Client

import scala.io.Source

object BotField extends UAField {
  val name = "bot"
  val validBrowsers = Set("chrome", "mobile safari", "chrome mobile", "firefox", "samsung internet", "edge", "ie")
  val knownBots     = Set("apache-httpclient", "googlebot", "adsbot-google", "ahrefsbot", "bingpreview")

  // https://github.com/JayBizzle/Crawler-Detect/blob/master/raw/Crawlers.txt
  val patterns = Source.fromResource("crawlers.dat").getLines().map(_.r).toList

  override def value(client: Client): Option[String] = {
    val family      = client.userAgent.family.toLowerCase
    val endsWithBot = family.endsWith("bot") || family.endsWith("crawler") || family.endsWith("spider")
    val isBot = !validBrowsers.contains(family) && (endsWithBot || knownBots.contains(family) || patterns.exists(
      _.findFirstIn(family).isDefined
    ))
    if (isBot) Some("bot") else None
  }

  override def possibleValues: List[String] = List("bot")

}
