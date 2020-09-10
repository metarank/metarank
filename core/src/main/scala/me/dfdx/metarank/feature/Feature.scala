package me.dfdx.metarank.feature

import java.io.{DataInput, DataOutput}

import me.dfdx.metarank.model.{Event, Timestamp}
import me.dfdx.metarank.state.State

trait Feature[T <: State] {
  def name: String
  def onEvent(state: T, event: Event): State
  def values(state: T): Array[Float]
}
