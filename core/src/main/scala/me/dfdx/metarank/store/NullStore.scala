package me.dfdx.metarank.store
import cats.effect.IO
import me.dfdx.metarank.aggregation.Aggregation
import me.dfdx.metarank.aggregation.state.State

object NullStore extends Store {
  override def load[T <: State](tracker: Aggregation, scope: Aggregation.Scope)(implicit
      reader: State.Reader[T]
  ): IO[Option[T]] = IO.pure(None)

  override def save[T <: State](tracker: Aggregation, scope: Aggregation.Scope, value: T)(implicit
      writer: State.Writer[T]
  ): IO[Unit] = IO.unit
}
