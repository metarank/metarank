package ai.metarank.util

import ai.metarank.util.RefererParser.{ExternalReferer, InternalReferer, UnknownReferer}
import better.files.Resource
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.net.URI

class RefererParserTest extends AnyFlatSpec with Matchers {
  lazy val parser = RefererParser.fromString(Resource.my.getAsString("/referers.json")) match {
    case Left(err)    => throw err
    case Right(value) => value
  }

  it should "parse a search referer with a term" in {
    parser.parse("http://www.google.com/search?q=hello") shouldBe Some(
      ExternalReferer("search", "Google", Some("hello"))
    )
  }

  it should "match a known host after stripping subdomains" in {
    parser.parse("http://www.facebook.com/profile.php?id=1") shouldBe Some(ExternalReferer("social", "Facebook", None))
  }

  it should "detect internal referers by page host" in {
    parser.parse(new URI("http://www.example.com/about"), Some("www.example.com"), Nil) shouldBe Some(InternalReferer)
  }

  it should "return UnknownReferer for unrecognized domains" in {
    parser.parse("https://www.example.org/") shouldBe Some(UnknownReferer)
  }

  it should "return None for invalid schemes and garbage" in {
    parser.parse("ftp://google.com/") shouldBe None
    parser.parse("not a valid url") shouldBe None
    parser.parse("") shouldBe None
  }
}
