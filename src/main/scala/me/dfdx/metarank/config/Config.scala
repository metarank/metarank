package me.dfdx.metarank.config

import me.dfdx.metarank.config.Config.ListenConfig

case class Config(listen: ListenConfig)

object Config {
  case class ListenConfig(hostname: String, port: Int)
}
