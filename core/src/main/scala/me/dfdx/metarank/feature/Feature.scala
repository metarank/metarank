package me.dfdx.metarank.feature

import java.io.{DataInput, DataOutput}

import cats.effect.IO
import me.dfdx.metarank.model.{Event, Timestamp}
import me.dfdx.metarank.state.State
import me.dfdx.metarank.store.Store

trait Feature[T <: State] {
  def readState(store: Store): IO[T]
  def onEvent(state: T, event: Event): Option[T]
  def writeState(store: Store, state: T): IO[Unit]
  def values(state: T): Array[Float]
}
