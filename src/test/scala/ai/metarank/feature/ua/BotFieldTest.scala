package ai.metarank.feature.ua

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import ua_parser.Parser

class BotFieldTest extends AnyFlatSpec with Matchers with TableDrivenPropertyChecks {
  lazy val parser = new Parser()

  val values = Table(
    ("ua", "bot"),
    (
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.102 Safari/537.36",
      None
    ),
    (
      "Mozilla/5.0 (iPhone; CPU iPhone OS 13_5_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.1.1 Mobile/15E148 Safari/604.1",
      None
    ),
    (
      "Mozilla/5.0 (iPhone; CPU iPhone OS 14_4_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0.3 Mobile/15E148 Safari/604.1",
      None
    ),
    ("Mozilla/4.0 (compatible; MSIE 9.0; Windows NT 6.1)", None),
    (
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.102 Safari/537.36 Edge/18.18363",
      None
    ),
    (
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.2 Safari/605.1.15",
      None
    ),
    (
      "Mozilla/5.0 (Linux; Android 11; Samsung SM-A025G) AppleWebKit/535.19 (KHTML, like Gecko) Chrome/18.0.1025.166 Mobile Safari/535.19",
      None
    ),
    (
      "Mozilla/5.0 (iPad; CPU OS 15_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/99.0.4844.47 Mobile/15E148 Safari/604.1",
      None
    ),
    ("whatever", None),
    ("", None),
    ("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", Some("bot"))
  )

  it should "parse bots" in {
    forAll(values) { (ua: String, value: Option[String]) =>
      BotField.value(parser.parse(ua)) shouldBe value
    }
  }

}
