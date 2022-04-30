package ai.metarank.config

import ai.metarank.config.EventSourceConfig.{KafkaSourceConfig, SourceOffset}
import ai.metarank.config.EventSourceConfig.SourceOffset._
import ai.metarank.config.EventSourceConfigTest.Source
import ai.metarank.config.MPath.LocalPath
import cats.data.NonEmptyList
import io.circe.Decoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.generic.semiauto._
import io.circe.yaml.parser.{parse => parseYaml}
import io.circe.parser._

import scala.concurrent.duration._

class EventSourceConfigTest extends AnyFlatSpec with Matchers {
  import EventSourceConfig._
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

  it should "decode kafka config" in {
    val yaml = """type: kafka
                 |brokers: [broker1, broker2]
                 |topic: events
                 |groupId: metarank
                 |offset: earliest""".stripMargin
    val decoded = parseYaml(yaml).flatMap(_.as[EventSourceConfig])
    decoded shouldBe Right(
      KafkaSourceConfig(
        brokers = NonEmptyList.of("broker1", "broker2"),
        topic = "events",
        groupId = "metarank",
        offset = SourceOffset.Earliest
      )
    )
  }

  it should "decode pulsar config" in {
    val yaml = """type: pulsar
                 |serviceUrl: service
                 |adminUrl: admin
                 |topic: events
                 |subscriptionName: metarank
                 |subscriptionType: exclusive 
                 |offset: earliest
                 |""".stripMargin
    val decoded = parseYaml(yaml).flatMap(_.as[EventSourceConfig])
    decoded shouldBe Right(
      PulsarSourceConfig(
        serviceUrl = "service",
        adminUrl = "admin",
        topic = "events",
        subscriptionName = "metarank",
        subscriptionType = "exclusive",
        offset = SourceOffset.Earliest
      )
    )
  }

  it should "decode rest config" in {
    val yaml    = "type: rest"
    val decoded = parseYaml(yaml).flatMap(_.as[EventSourceConfig])
    decoded shouldBe Right(
      RestSourceConfig(
        bufferSize = 10000,
        host = "localhost",
        port = 8080
      )
    )
  }

  it should "decode file config" in {
    val yaml = """type: file
                 |path: file:///ranklens/events/""".stripMargin
    val decoded = parseYaml(yaml).flatMap(_.as[EventSourceConfig])
    decoded shouldBe Right(
      FileSourceConfig(
        path = LocalPath("/ranklens/events/")
      )
    )
  }
}

object EventSourceConfigTest {
  case class Source(offset: SourceOffset)
  implicit val sourceTestDecoder: Decoder[Source] = deriveDecoder
}
