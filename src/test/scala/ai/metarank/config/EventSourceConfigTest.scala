package ai.metarank.config

import ai.metarank.config.EventSourceConfig.SourceOffset
import ai.metarank.config.EventSourceConfig.SourceOffset._
import ai.metarank.config.EventSourceConfigTest.Source
import io.circe.Decoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.generic.semiauto._
import io.circe.yaml.parser.{parse => parseYaml}
import io.circe.parser._
import scala.concurrent.duration._

class EventSourceConfigTest extends AnyFlatSpec with Matchers {
  def parse(offset: String) = {
    parseYaml(s"offset: $offset").flatMap(_.as[Source])
  }
  it should "decode offset formats" in {
    parse("earliest") shouldBe Right(Source(Earliest))
    parse("latest") shouldBe Right(Source(Latest))
    parse("id=1234") shouldBe Right(Source(ExactOffset(1234)))
    parse("ts=1234") shouldBe Right(Source(ExactTimestamp(1234)))
    parse("last=60d") shouldBe Right(Source(RelativeDuration(60.days)))
    parse("last=60").isLeft shouldBe true
  }
}

object EventSourceConfigTest {
  case class Source(offset: SourceOffset)
  implicit val sourceTestDecoder: Decoder[Source] = deriveDecoder
}
