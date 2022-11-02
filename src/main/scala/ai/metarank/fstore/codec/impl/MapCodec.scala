package ai.metarank.fstore.codec.impl

import java.io.{DataInput, DataOutput}

class MapCodec[K, V](kc: BinaryCodec[K], vc: BinaryCodec[V]) extends BinaryCodec[Map[K, V]] {
  import CodecOps._
  override def read(in: DataInput): Map[K, V] = {
    val size   = in.readVarInt()
    val values = (0 until size).map(_ => kc.read(in) -> vc.read(in))
    values.toMap
  }
  override def write(value: Map[K, V], out: DataOutput): Unit = {
    out.writeVarInt(value.size)
    value.foreach { case (k, v) =>
      kc.write(k, out)
      vc.write(v, out)
    }
  }
}
