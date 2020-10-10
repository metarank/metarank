package me.dfdx.metarank.store.state.codec

import me.dfdx.metarank.aggregation.Scope

trait KeyCodec[T] {
  def write(value: T): String
}

object KeyCodec {}
