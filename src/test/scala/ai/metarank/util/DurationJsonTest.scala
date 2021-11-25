package ai.metarank.util

import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.FiniteDuration

class DurationJsonTest extends AnyFlatSpec with Matchers {
  it should "decode durations" in {
    import DurationJson._
    import scala.concurrent.duration._
    parse("1d").flatMap(_.as[FiniteDuration]) shouldBe Right(1.day)
    parse("1s").flatMap(_.as[FiniteDuration]) shouldBe Right(1.second)
    parse("1m").flatMap(_.as[FiniteDuration]) shouldBe Right(1.minute)
    parse("1h").flatMap(_.as[FiniteDuration]) shouldBe Right(1.hour)
  }

}
