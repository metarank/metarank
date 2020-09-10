package me.dfdx.metarank.state

import java.io.{DataInput, DataOutput}

import me.dfdx.metarank.model.Timestamp

trait State {
  def updatedAt: Timestamp
}

object State {
  trait Reader[T <: State] {
    def read(in: DataInput): T
  }

  trait Writer[T <: State] {
    def write(value: T, out: DataOutput): Unit
  }
}
