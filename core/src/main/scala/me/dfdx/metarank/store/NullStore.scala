package me.dfdx.metarank.store
import cats.effect.IO
import me.dfdx.metarank.aggregation.Aggregation
import me.dfdx.metarank.store.state.State
import me.dfdx.metarank.store.state.State.ValueState

object NullStore extends Store {
  override def get[T](desc: ValueState[T], scope: Aggregation.Scope): IO[Option[T]]      = IO.pure(None)
  override def put[T](desc: ValueState[T], scope: Aggregation.Scope, value: T): IO[Unit] = IO.unit

  override def get[K, V](desc: State.MapState[K, V], scope: Aggregation.Scope, key: K): IO[Option[V]] = IO.pure(None)

  override def put[K, V](desc: State.MapState[K, V], scope: Aggregation.Scope, key: K, value: V): IO[Unit] = IO.unit
}
