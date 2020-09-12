package me.dfdx.metarank.store

import cats.effect.IO
import me.dfdx.metarank.tracker.Tracker
import me.dfdx.metarank.tracker.state.State

trait Store {
  def load[T <: State](tracker: String, scope: Tracker.Scope)(implicit reader: State.Reader[T]): IO[Option[T]]
  def save[T <: State](tracker: String, scope: Tracker.Scope, value: T)(implicit writer: State.Writer[T]): IO[Unit]
  def key(tracker: String, scope: Tracker.Scope) = s"$tracker|${scope.key}"
}
