package me.dfdx.metarank.feature

import java.io.{DataInput, DataOutput}

trait Feature {
  def name: String
  def write(out: DataOutput): Unit
}

object Feature {
  trait Loader[T <: Feature] {
    def load(in: DataInput): T
  }
}
