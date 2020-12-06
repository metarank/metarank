package me.dfdx.metarank.config

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser._
import me.dfdx.metarank.config.Config.WindowConfig
import me.dfdx.metarank.config.FeatureConfig.{CountFeatureConfig, QueryMatchFeatureConfig}
import me.dfdx.metarank.model.Nel

class FeatureConfigTest extends AnyFlatSpec with Matchers {
  it should "decode count feature config" in {
    val yaml =
      """type: count
        |windows: [ { from: 1, length: 7 } ]
        |""".stripMargin
    parse(yaml).flatMap(_.as[FeatureConfig]) shouldBe Right(
      CountFeatureConfig(
        windows = Nel(WindowConfig(1, 7))
      )
    )
  }

  it should "decode query-field matching config" in {
    val yaml =
      """type: field_match
        |field: title
        |""".stripMargin
    parse(yaml).flatMap(_.as[FeatureConfig]) shouldBe Right(QueryMatchFeatureConfig("title"))
  }

}
