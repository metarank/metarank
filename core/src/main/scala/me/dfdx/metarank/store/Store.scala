package me.dfdx.metarank.store

import cats.effect.IO
import me.dfdx.metarank.aggregation.Aggregation
import me.dfdx.metarank.aggregation.state.State

trait Store {
  def load[T <: State](tracker: Aggregation, scope: Aggregation.Scope)(implicit
      reader: State.Reader[T]
  ): IO[Option[T]]
  def save[T <: State](tracker: Aggregation, scope: Aggregation.Scope, value: T)(implicit
      writer: State.Writer[T]
  ): IO[Unit]
  protected def key[T <: State](tracker: Aggregation, scope: Aggregation.Scope) =
    s"${tracker.name}/${scope.key}"
}
