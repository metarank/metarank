package me.dfdx.metarank.config

import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser._
import io.circe.yaml.syntax._
import io.circe.syntax._
import io.circe._
import me.dfdx.metarank.config.Config.{EventType, WindowConfig}
import me.dfdx.metarank.config.FeatureConfig.CountFeatureConfig
import me.dfdx.metarank.model.Nel

class FeatureConfigTest extends AnyFlatSpec with Matchers {
  it should "decode count feature config" in {
    val yaml =
      """type: count
        |events: ["search"]
        |windows: [ { from: 1, length: 7 } ]
        |""".stripMargin
    parse(yaml).flatMap(_.as[FeatureConfig]) shouldBe Right(
      CountFeatureConfig(
        events = Nel(EventType("search")),
        windows = Nel(WindowConfig(1, 7))
      )
    )
  }
}
