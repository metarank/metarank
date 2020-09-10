package me.dfdx.metarank.state

import java.io.{DataInput, DataOutput}

import me.dfdx.metarank.model.Timestamp
import me.dfdx.metarank.state.State.StateReadException

case class CircularReservoir(updatedAt: Timestamp, lastDay: Int, size: Int, buffer: Vector[Int]) extends State {
  def sumLast(days: Int): Int = {
    var sum = 0
    var pos = math.max(lastDay - days, 0)
    while (pos < lastDay) {
      sum += buffer(wrap(pos))
      pos += 1
    }
    sum
  }

  def increment(ts: Timestamp, value: Int = 1): CircularReservoir = {
    val day = ts.day
    if (lastDay == 0) {
      // initial increment
      val position = wrap(day)
      copy(updatedAt = ts, lastDay = day, buffer = buffer.updated(position, buffer(position) + value))
    } else if (day == lastDay) {
      // same day increment
      val position = wrap(day)
      copy(buffer = buffer.updated(position, buffer(position) + value))
    } else {
      // the day is incremented
      if (day - lastDay >= size) {
        // we have a full loop over circular buffer, so we need to wipe everything
        copy(updatedAt = ts, lastDay = day, buffer = Vector.fill(size)(0).updated(wrap(day), value))
      } else {
        // we're within the buffer size,
        // so we iterate from the next day to the current one, zeroing everything in between
        // in case if single day increment, (lastDay+1) == ts.day, so this loop is skipped
        val skipped = ((lastDay + 1) until day).foldLeft(buffer)((buf, iday) => buf.updated(wrap(iday), 0))
        copy(updatedAt = ts, lastDay = day, buffer = skipped.updated(wrap(day), value))
      }
    }
  }

  def wrap(day: Int) = day % size
}

object CircularReservoir {
  def apply(windowSizeDays: Int) =
    new CircularReservoir(Timestamp(0), 0, windowSizeDays, Vector.fill(windowSizeDays)(0))

  implicit val crReader = new State.Reader[CircularReservoir] {
    override def read(in: DataInput): CircularReservoir = {
      val updatedAt = Timestamp(in.readLong())
      val lastDay   = in.readInt()
      val size      = in.readInt()
      val values    = for (_ <- 0 until size) yield { in.readInt() }
      new CircularReservoir(updatedAt, lastDay, size, values.toVector)
    }
  }

  implicit val ctWriter = new State.Writer[CircularReservoir] {
    override def write(value: CircularReservoir, out: DataOutput): Unit = {
      out.writeLong(value.updatedAt.value)
      out.writeInt(value.lastDay)
      out.writeInt(value.size)
      value.buffer.foreach(out.writeInt)
    }
  }
}
