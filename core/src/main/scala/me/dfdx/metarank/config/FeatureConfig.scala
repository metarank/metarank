package me.dfdx.metarank.config

import cats.data.NonEmptyList
import io.circe.{Codec, Decoder, Encoder, Json}
import io.circe.generic.extras.Configuration
import me.dfdx.metarank.config.Config.{EventType, WindowConfig}
import io.circe.generic.extras.semiauto._

sealed trait FeatureConfig {
  def events: NonEmptyList[EventType]
}

object FeatureConfig {
  case class CountFeatureConfig(events: NonEmptyList[EventType], windows: NonEmptyList[WindowConfig])
      extends FeatureConfig {
    val maxDate = windows.map(w => w.from + w.length).reduceLeft(_ + _)
  }
  implicit val conf = Configuration.default
    .withDiscriminator("type")
    .copy(transformConstructorNames = _ match {
      case "CountFeatureConfig" => "count"
    })

  implicit val featureConfigCodec: Codec[FeatureConfig] = deriveConfiguredCodec[FeatureConfig]
}
