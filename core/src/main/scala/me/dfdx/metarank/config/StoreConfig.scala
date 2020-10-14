package me.dfdx.metarank.config

import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._

sealed trait StoreConfig

object StoreConfig {
  case class HeapStoreConfig()                         extends StoreConfig
  case class HeapBytesStoreConfig()                    extends StoreConfig
  case class RedisStoreConfig(host: String, port: Int) extends StoreConfig
  case class NullStoreConfig()                         extends StoreConfig

  implicit val conf = Configuration.default
    .withDiscriminator("type")
    .copy(transformConstructorNames = _ match {
      case "HeapBytesStoreConfig" => "heap_bytes"
      case "HeapStoreConfig"      => "heap"
      case "RedisStoreConfig"     => "redis"
      case "NullStoreConfig"      => "null"
    })

  implicit val storeConfigCodec: Codec[StoreConfig] = deriveConfiguredCodec[StoreConfig]
}
