package ai.metarank.fstore.redis.codec.impl

import java.io.{DataInput, DataOutput}

trait BinaryCodec[T] {
  def write(value: T, out: DataOutput): Unit
  def read(in: DataInput): T
}

object BinaryCodec {
  import CodecOps._
  val int = new BinaryCodec[Int] {
    override def read(in: DataInput): Int                 = in.readVarInt()
    override def write(value: Int, out: DataOutput): Unit = out.writeVarInt(value)
  }

  val long = new BinaryCodec[Long] {
    override def read(in: DataInput): Long                 = in.readVarLong()
    override def write(value: Long, out: DataOutput): Unit = out.writeVarLong(value)
  }

  val double = new BinaryCodec[Double] {
    override def read(in: DataInput): Double                 = in.readDouble()
    override def write(value: Double, out: DataOutput): Unit = out.writeDouble(value)
  }

  val string = new BinaryCodec[String] {
    override def read(in: DataInput): String                 = in.readUTF()
    override def write(value: String, out: DataOutput): Unit = out.writeUTF(value)
  }
}
