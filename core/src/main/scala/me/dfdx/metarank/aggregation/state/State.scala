package me.dfdx.metarank.aggregation.state

import java.io.{ByteArrayOutputStream, DataInput, DataOutput, DataOutputStream}

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
    def write(value: T): Array[Byte] = {
      val buffer = new ByteArrayOutputStream()
      write(value, new DataOutputStream(buffer))
      buffer.toByteArray
    }
  }

  case class StateReadError(msg: String) extends Exception(msg)
}
