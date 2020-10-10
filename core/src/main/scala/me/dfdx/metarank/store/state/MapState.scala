package me.dfdx.metarank.store.state

import cats.effect.IO

trait MapState[K, V] {
  def get(key: K): IO[Option[V]]
  def put(key: K, value: V): IO[Unit]
  def delete(key: K): IO[Unit]
}
