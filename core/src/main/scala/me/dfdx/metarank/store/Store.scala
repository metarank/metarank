package me.dfdx.metarank.store

import cats.effect.IO
import me.dfdx.metarank.config.Config.InteractionType
import me.dfdx.metarank.state.State

trait Store {
  def load[T <: State](key: String)(implicit reader: State.Reader[T]): IO[Option[T]]
  def save[T <: State](key: String, value: T)(implicit writer: State.Writer[T]): IO[Unit]
}

object Store {
  trait Key[T] {
    def string(key: T): String
  }

  implicit val interactionKey = new Key[InteractionType] {
    override def string(key: InteractionType): String = key.value
  }
}
