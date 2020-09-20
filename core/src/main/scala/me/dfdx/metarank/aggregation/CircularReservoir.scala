package me.dfdx.metarank.aggregation

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInput, DataInputStream, DataOutput, DataOutputStream}

import me.dfdx.metarank.model.Timestamp
import me.dfdx.metarank.store.state.StateDescriptor
import me.dfdx.metarank.store.state.codec.Codec

case class CircularReservoir(updatedAt: Timestamp, lastDay: Int, size: Int, buffer: Vector[Int]) {
  def sum(from: Int, length: Int): Int = {
    var sum = 0
    // as length cannot be more than buffer size
    val effectiveLength = math.min(length - 1, size - 2)
    // we always start from the previous day going backwards
    var start = math.max(lastDay - from - effectiveLength, 0)
    val end   = math.max(lastDay - from, 0)
    while (start <= end) {
      val pos = wrap(start)
      sum += buffer(pos)
      start += 1
    }
    sum
  }

  def sumLast(days: Int): Int = {
    sum(1, days)
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

  implicit val ctReaderWriter = new Codec[CircularReservoir] {
    override def read(bytes: Array[Byte]): CircularReservoir = {
      val in        = new DataInputStream(new ByteArrayInputStream(bytes))
      val updatedAt = Timestamp(in.readLong())
      val lastDay   = in.readInt()
      val size      = in.readInt()
      val values    = for (_ <- 0 until size) yield { in.readInt() }
      new CircularReservoir(updatedAt, lastDay, size, values.toVector)
    }

    override def write(value: CircularReservoir): Array[Byte] = {
      val buffer = new ByteArrayOutputStream()
      val out    = new DataOutputStream(buffer)
      out.writeLong(value.updatedAt.value)
      out.writeInt(value.lastDay)
      out.writeInt(value.size)
      value.buffer.foreach(out.writeInt)
      buffer.toByteArray
    }
  }
}
