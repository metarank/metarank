package me.dfdx.metarank.state

import java.io.DataOutput

import me.dfdx.metarank.model.Timestamp

trait State {
  def updatedAt: Timestamp
  def write(out: DataOutput): Unit
}
