package ai.metarank.fstore.codec.impl

import ai.metarank.model.Scalar
import ai.metarank.model.Scalar.{SBoolean, SDouble, SDoubleList, SString, SStringList}

import java.io.{DataInput, DataOutput}

object ScalarCodec extends BinaryCodec[Scalar] {
  import CodecOps._

  def write(value: Scalar, out: DataOutput): Unit = value match {
    case Scalar.SString(value) =>
      out.writeByte(0)
      out.writeUTF(value)
    case Scalar.SDouble(value) =>
      out.writeByte(1)
      out.writeDouble(value)
    case Scalar.SBoolean(value) =>
      out.writeByte(2)
      out.writeBoolean(value)
    case Scalar.SStringList(value) =>
      out.writeByte(3)
      out.writeVarInt(value.length)
      value.foreach(out.writeUTF)
    case Scalar.SDoubleList(value) =>
      out.writeByte(4)
      out.writeVarInt(value.length)
      value.foreach(out.writeDouble)
  }

  def read(in: DataInput): Scalar = in.readByte() match {
    case 0 => SString(in.readUTF())
    case 1 => SDouble(in.readDouble())
    case 2 => SBoolean(in.readBoolean())
    case 3 =>
      val size  = in.readVarInt()
      val items = (0 until size).map(_ => in.readUTF()).toList
      SStringList(items)
    case 4 =>
      val size = in.readVarInt()
      val buf  = new Array[Double](size)
      var i    = 0
      while (i < size) {
        buf(i) = in.readDouble()
        i += 1
      }
      SDoubleList(buf)
    case index => throw new Exception(s"cannot decode scalar $index")
  }

}
