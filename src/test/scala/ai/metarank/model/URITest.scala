package ai.metarank.model

import ai.metarank.model.URI.{LocalURI, NullURI, RedisURI, S3URI}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

class URITest extends AnyFlatSpec with Matchers with TableDrivenPropertyChecks {
  val validCases = Table(
    ("str", "uri"),
    ("s3://bucket/prefix", S3URI("bucket", "prefix")),
    ("s3://bucket/prefix/dir?yolo", S3URI("bucket", "prefix/dir?yolo")),
    ("/dev/null", NullURI),
    ("null", NullURI),
    ("/home/dir", LocalURI("/home/dir")),
    ("dir", LocalURI(LocalURI.cwd + "/dir")),
    ("redis://host:1234", RedisURI("host", 1234)),
    ("redis://password@host:1234", RedisURI("host", 1234, password = Some("password"))),
    ("redis://user:password@host:1234", RedisURI("host", 1234, user = Some("user"), password = Some("password")))
  )

  val invalidCases = Table(
    ("str"),
    ("s3:///prefix")
  )
  it should "parse things" in {
    forAll(validCases) { (source: String, uri: URI) =>
      URI.parse(source) shouldBe Right(uri)
    }
  }

  it should "fail on errors things" in {
    forAll(invalidCases) { (source: String) =>
      val parsed = URI.parse(source)
      parsed shouldBe a[Left[_, _]]
    }
  }
}
