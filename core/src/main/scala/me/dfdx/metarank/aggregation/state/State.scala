package me.dfdx.metarank.aggregation.state

import java.io.{DataInput, DataOutput}

import me.dfdx.metarank.model.Timestamp

trait State {
  def updatedAt: Timestamp
  def write(out: DataOutput): Unit
}

object State {
  trait Reader[T <: State] {
    def read(in: DataInput): T
  }

  trait Writer[T <: State] {
    def write(value: T, out: DataOutput): Unit
  }

  case class StateReadError(msg: String) extends Exception(msg)
}
