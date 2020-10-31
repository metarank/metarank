package me.dfdx.metarank.store.state.codec

import java.io.{DataInput, DataOutput}

trait Codec[T] {
  def read(in: DataInput): T
  def write(value: T, out: DataOutput): Unit
}
