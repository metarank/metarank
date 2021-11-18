package ai.metarank.config

import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._

sealed trait ValueStoreConfig {}

object ValueStoreConfig {
  case class RedisStoreConfig(host: String, port: Int) extends ValueStoreConfig

  implicit val config = Configuration.default
    .withDiscriminator("type")
    .copy(transformConstructorNames = { case "RedisStoreConfig" =>
      "redis"
    })

  implicit val storeDecoder: Decoder[ValueStoreConfig] = deriveConfiguredDecoder
}
