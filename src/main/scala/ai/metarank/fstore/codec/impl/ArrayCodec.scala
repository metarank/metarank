package ai.metarank.fstore.codec.impl

import java.io.{DataInput, DataOutput}
import scala.reflect.ClassTag

class ArrayCodec[T: ClassTag](ec: BinaryCodec[T]) extends BinaryCodec[Array[T]] {
  import CodecOps._
  override def read(in: DataInput): Array[T] = {
    val size  = in.readVarInt()
    val items = new Array[T](size)
    var i     = 0
    while (i < size) {
      items(i) = ec.read(in)
      i += 1
    }
    items
  }

  override def write(value: Array[T], out: DataOutput): Unit = {
    out.writeVarInt(value.length)
    var i = 0
    while (i < value.length) {
      ec.write(value(i), out)
      i += 1
    }
  }
}
