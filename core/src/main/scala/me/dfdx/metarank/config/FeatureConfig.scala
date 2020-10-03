package me.dfdx.metarank.config

import cats.data.NonEmptyList
import me.dfdx.metarank.config.Config.WindowConfig
import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._

sealed trait FeatureConfig

object FeatureConfig {
  case class CountFeatureConfig(windows: NonEmptyList[WindowConfig]) extends FeatureConfig {
    val maxDate = windows.map(w => w.from + w.length).reduceLeft(_ + _)
  }

  implicit val conf = Configuration.default
    .withDiscriminator("type")
    .withKebabCaseMemberNames
    .copy(transformConstructorNames = { case "CountFeatureConfig" =>
      "count"
    })

  implicit val featureConfigCodec: Codec[FeatureConfig] = deriveConfiguredCodec[FeatureConfig]
}
