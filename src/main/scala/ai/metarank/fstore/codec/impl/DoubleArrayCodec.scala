package ai.metarank.fstore.codec.impl

import ai.metarank.fstore.codec.impl.CodecOps.{DataInputOps, DataOutputOps}

import java.io.{DataInput, DataOutput}

object DoubleArrayCodec extends BinaryCodec[Array[Double]] {
  override def read(in: DataInput): Array[Double] = {
    val size   = in.readVarInt()
    val buffer = new Array[Double](size)
    var i      = 0
    while (i < size) {
      buffer(i) = in.readDouble()
      i += 1
    }
    buffer
  }

  override def write(value: Array[Double], out: DataOutput): Unit = {
    out.writeVarInt(value.length)
    var i = 0
    while (i < value.length) {
      out.writeDouble(value(i))
      i += 1
    }
  }
}
