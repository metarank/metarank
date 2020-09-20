package me.dfdx.metarank.store.state

import cats.effect.IO

trait ValueState[T] {
  def get(): IO[Option[T]]
  def put(value: T): IO[Unit]
  def delete(): IO[Unit]
}
