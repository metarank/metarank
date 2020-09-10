package me.dfdx.metarank.store

import me.dfdx.metarank.config.Config.InteractionType

trait Store {}

object Store {
  trait Key[T] {
    def string(key: T): String
  }

  implicit val interactionKey = new Key[InteractionType] {
    override def string(key: InteractionType): String = key.value
  }
}
