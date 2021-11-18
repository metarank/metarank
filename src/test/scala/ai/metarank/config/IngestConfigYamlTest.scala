package ai.metarank.config

import ai.metarank.config.IngestConfig.{APIIngestConfig, FileIngestConfig}
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IngestConfigYamlTest extends AnyFlatSpec with Matchers {
  it should "decode file config" in {
    parse("type: file\npath: /foo").flatMap(_.as[IngestConfig]) shouldBe Right(FileIngestConfig("/foo"))
  }

  it should "decode api config" in {
    parse("type: api\nport: 1234").flatMap(_.as[IngestConfig]) shouldBe Right(APIIngestConfig(1234))
  }
}
