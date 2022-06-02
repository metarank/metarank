package ai.metarank.config

import MPath.{LocalPath, S3Path}
import ai.metarank.config.MPathTest.Something
import io.circe.Decoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser._
import io.circe.generic.semiauto._
import better.files.Dsl.cwd

class MPathTest extends AnyFlatSpec with Matchers {
  it should "decode s3 paths" in {
    parse("s3://bucket/prefix/dir") shouldBe Right(S3Path("bucket", "prefix/dir"))
  }

  it should "decode s3 paths with dash" in {
    parse("s3://bucket-x/prefix") shouldBe Right(S3Path("bucket-x", "prefix"))
  }

  it should "decode file:// paths with double slashes" in {
    parse("file://home/foo") shouldBe Right(LocalPath("/home/foo"))
  }

  it should "decode file:// paths with triple slashes" in {
    parse("file:///home/foo") shouldBe Right(LocalPath("/home/foo"))
  }

  it should "decode local relative paths" in {
    parse("src") shouldBe Right(LocalPath((cwd / "src").toString()))
  }

  def parse(path: String) = {
    val json = s"""{"x": "$path"}"""
    decode[Something](json).map(_.x)
  }
}

object MPathTest {
  case class Something(x: MPath)
  implicit val someDecoder: Decoder[Something] = deriveDecoder
}
