package ai.metarank.mode

import ai.metarank.mode.inference.InferenceCmdline
import better.files.File
import cats.effect.unsafe.implicits.global
import io.findify.featury.values.StoreCodec.JsonCodec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Try

class InferenceCmdlineTest extends AnyFlatSpec with Matchers {
  it should "parse explicit options" in {
    val result = InferenceCmdline
      .parse(List("--savepoint-dir", "foo", "--model", "foo", "--config", "foo", "--format", "json"), Map.empty)
      .unsafeRunSync()
    result shouldBe InferenceCmdline(config = "foo", format = JsonCodec, savepoint = "foo")
  }

  it should "parse options from env" in {
    val result = InferenceCmdline
      .parse(List("--model", "foo", "--config", "foo", "--format", "json"), Map("METARANK_SAVEPOINT_DIR" -> "bar"))
      .unsafeRunSync()
    result shouldBe InferenceCmdline(config = "foo", format = JsonCodec, savepoint = "bar")
  }

  it should "fail on missing params" in {
    val result = Try(
      InferenceCmdline
        .parse(List("--model", "foo", "--config", "foo", "--format", "json"), Map.empty)
        .unsafeRunSync()
    )
    result.isFailure shouldBe true
  }
}
