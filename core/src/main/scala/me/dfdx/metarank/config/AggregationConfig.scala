package me.dfdx.metarank.config

import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._

sealed trait AggregationConfig {}

object AggregationConfig {
  case class CountAggregationConfig(daysBack: Int) extends AggregationConfig

  implicit val conf = Configuration.default
    .withDiscriminator("type")
    .withKebabCaseMemberNames
    .copy(transformConstructorNames = { case "CountAggregationConfig" =>
      "count"
    })

  implicit val aggConfigCodec: Codec[AggregationConfig] = deriveConfiguredCodec[AggregationConfig]

}
