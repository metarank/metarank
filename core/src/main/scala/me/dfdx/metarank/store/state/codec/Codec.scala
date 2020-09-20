package me.dfdx.metarank.store.state.codec

trait Codec[T] {
  def read(in: Array[Byte]): T
  def write(value: T): Array[Byte]
}
