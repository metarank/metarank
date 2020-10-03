package me.dfdx.metarank.store
import cats.effect.IO
import me.dfdx.metarank.aggregation.{Aggregation, Scope}
import me.dfdx.metarank.store.state.StateDescriptor.{MapStateDescriptor, ValueStateDescriptor}
import me.dfdx.metarank.store.state.{MapState, StateDescriptor, ValueState}

object NullStore extends Store {
  class NullMapState[K, V] extends MapState[K, V] {
    override def delete(key: K): IO[Unit]        = IO.unit
    override def get(key: K): IO[Option[V]]      = IO.pure(None)
    override def put(key: K, value: V): IO[Unit] = IO.unit
    override def values(): IO[Map[K, V]]         = IO.pure(Map.empty)
  }
  class NullValueState[T] extends ValueState[T] {
    override def delete(): IO[Unit]      = IO.unit
    override def get(): IO[Option[T]]    = IO.pure(None)
    override def put(value: T): IO[Unit] = IO.unit
  }
  override def kv[K, V](desc: MapStateDescriptor[K, V], scope: Scope): MapState[K, V] =
    new NullMapState[K, V]()

  override def value[T](desc: ValueStateDescriptor[T], scope: Scope): ValueState[T] =
    new NullValueState[T]()
}
