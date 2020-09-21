package me.dfdx.metarank.store.state

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import cats.effect.IO
import me.dfdx.metarank.aggregation.Aggregation.Scope

trait MapState[K, V] {
  def get(key: K): IO[Option[V]]
  def put(key: K, value: V): IO[Unit]
  def values(): IO[Map[K, V]]
  def delete(key: K): IO[Unit]

  protected def keyBytes(scope: Scope, key: Array[Byte]): ByteBuffer = {
    ByteBuffer.wrap(scope.key.getBytes(StandardCharsets.UTF_8) ++ key)
  }
}
