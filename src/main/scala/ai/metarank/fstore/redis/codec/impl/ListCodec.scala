package ai.metarank.fstore.redis.codec.impl
import java.io.{DataInput, DataOutput}

class ListCodec[T](ec: BinaryCodec[T]) extends BinaryCodec[List[T]] {
  import CodecOps._
  override def read(in: DataInput): List[T] = {
    val size  = in.readVarInt()
    val items = (0 until size).map(_ => ec.read(in))
    items.toList
  }

  override def write(value: List[T], out: DataOutput): Unit = {
    out.writeVarInt(value.size)
    value.foreach(v => ec.write(v, out))
  }
}
