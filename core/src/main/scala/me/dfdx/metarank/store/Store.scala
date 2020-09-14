package me.dfdx.metarank.store

import java.nio.charset.StandardCharsets
import java.util

import cats.effect.IO
import me.dfdx.metarank.aggregation.Aggregation
import me.dfdx.metarank.store.state.State
import me.dfdx.metarank.store.state.State.{Codec, KeyCodec, MapState, ValueState}

trait Store {
  def get[T](desc: ValueState[T], scope: Aggregation.Scope): IO[Option[T]]
  def put[T](desc: ValueState[T], scope: Aggregation.Scope, value: T): IO[Unit]

  def get[K, V](desc: MapState[K, V], scope: Aggregation.Scope, key: K): IO[Option[V]]
  def put[K, V](desc: MapState[K, V], scope: Aggregation.Scope, key: K, value: V): IO[Unit]

  protected def keystr(desc: State, scope: Aggregation.Scope) =
    s"${desc.name}/${scope.key}"

  protected def keystr[K](desc: State, scope: Aggregation.Scope, key: K)(implicit codec: KeyCodec[K]) =
    s"${desc.name}/${scope.key}/${codec.write(key)}}"
}
