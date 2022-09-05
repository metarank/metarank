package ai.metarank.util.analytics

import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ErrorReporterTest extends AnyFlatSpec with Matchers {
  it should "init enabled without errors" in {
    ErrorReporter.init(true).unsafeRunSync() shouldBe {}
  }

  it should "init disabled without errors" in {
    ErrorReporter.init(false).unsafeRunSync() shouldBe {}
  }
}
