package me.dfdx.metarank.feature

import java.io.{DataInput, DataOutput}

import me.dfdx.metarank.model.{Event, Timestamp}

trait Feature {
  def updated: Timestamp
  def saved: Timestamp
  def size: Int
  def onEvent(event: Event): Feature
  //def write(out: DataOutput): Unit
  def values: Array[Float]
}

object Feature {
  trait Loader {
    def name: String
    //def read(in: DataInput): Feature
  }
}
