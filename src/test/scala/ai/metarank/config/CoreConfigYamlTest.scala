package ai.metarank.config

import ai.metarank.config.CoreConfig.{ImportCacheConfig, ImportConfig, TrackingConfig}
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

  it should "decode import cache options" in {
    val yaml =
      """import:
        |  cache:
        |    enabled: false
        |tracking:
        |  analytics: false
        |  errors: false""".stripMargin
    val conf = parse(yaml).flatMap(_.as[CoreConfig])
    conf shouldBe Right(
      CoreConfig(tracking = TrackingConfig(false, false), `import` = ImportConfig(ImportCacheConfig(enabled = false)))
    )

  }
}
