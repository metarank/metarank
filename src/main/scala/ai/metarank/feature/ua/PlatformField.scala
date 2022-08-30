package ai.metarank.feature.ua

import ai.metarank.feature.UserAgentFeature.UAField
import ai.metarank.model.Dimension.VectorDim
import ua_parser.Client

case object PlatformField extends UAField {
  val mobile = Set(
    "Amazon Silk",
    "Android",
    "BlackBerry WebKit",
    "Chrome Mobile",
    "Chrome Mobile iOS",
    "Edge Mobile",
    "Firefox Mobile",
    "IE Mobile",
    "Mobile Safari",
    "Mobile Safari UIWebView",
    "NetFront NX",
    "Opera Mini",
    "Opera Mobile",
    "QQ Browser",
    "QQ Browser Mobile",
    "UC Browser"
  )
  val desktop = Set(
    "Chrome",
    "Chrome Frame",
    "Chromium",
    "Edge",
    "Firefox",
    "IE",
    "Iron",
    "Maxthon",
    "Opera",
    "Safari",
    "SeaMonkey",
    "Yandex Browser"
  )

  override lazy val possibleValues = List("mobile", "desktop", "tablet")
  override lazy val dim            = VectorDim(possibleValues.size)

  override def value(client: Client): Option[String] = {
    if (client.os.family == "iOS") {
      if (client.device.family == "iPad") {
        Some("tablet")
      } else if (client.device.family == "iPhone") {
        Some("mobile")
      } else {
        None
      }
    } else if (client.os.family == "Android") {
      if (client.userAgent.family.contains("Mobile")) {
        Some("mobile")
      } else {
        Some("tablet")
      }
    } else if (mobile.contains(client.userAgent.family)) {
      Some("mobile")
    } else if (desktop.contains(client.userAgent.family)) {
      Some("desktop")
    } else {
      None
    }
  }
}
