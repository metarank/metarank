package ai.metarank.config

import ai.metarank.config.StateStoreConfig.RedisTLS
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.parse
import io.lettuce.core.SslVerifyMode

import java.nio.file.{Files, Paths}

class RedisTLSConfigTest extends AnyFlatSpec with Matchers {
  it should "decode tls with enabled=true" in {
    val yaml = parse("enabled: true").flatMap(_.as[RedisTLS])
    yaml shouldBe Right(RedisTLS(true))
  }

  it should "decode tls with proper file" in {
    val file = Files.createTempFile("ca", ".crt")
    val yaml = s"""enabled: true
                  |ca: ${file.toString}
                  |""".stripMargin
    val decoded = parse(yaml).flatMap(_.as[RedisTLS])
    decoded shouldBe Right(RedisTLS(true, Option(file.toFile)))
  }

  it should "fail on non-existent files" in {
    val yaml =
      s"""enabled: true
         |ca: /non/existent.ca
         |""".stripMargin
    val decoded = parse(yaml).flatMap(_.as[RedisTLS])
    decoded shouldBe a[Left[_, _]]
  }

  it should "decode verify type" in {
    val file = Files.createTempFile("ca", ".crt")
    val yaml =
      s"""enabled: true
         |ca: ${file.toString}
         |verify: ca
         |""".stripMargin
    val decoded = parse(yaml).flatMap(_.as[RedisTLS])
    decoded shouldBe Right(RedisTLS(true, Option(file.toFile), SslVerifyMode.CA))
  }
}
