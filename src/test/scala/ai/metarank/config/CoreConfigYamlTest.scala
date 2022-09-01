package ai.metarank.config

import ai.metarank.config.CoreConfig.TrackingConfig
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CoreConfigYamlTest extends AnyFlatSpec with Matchers {
  it should "decode tracking options" in {
    val yaml =
      """tracking:
        |  analytics: false
        |  errors: false""".stripMargin
    val conf = parse(yaml).flatMap(_.as[CoreConfig])
    conf shouldBe Right(CoreConfig(TrackingConfig(false, false)))
  }
}
