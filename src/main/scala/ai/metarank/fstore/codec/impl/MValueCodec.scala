package ai.metarank.fstore.codec.impl

import ai.metarank.fstore.codec.impl.CodecOps.{DataInputOps, DataOutputOps}
import ai.metarank.model.Dimension.VectorDim
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue
import ai.metarank.model.MValue.{CategoryValue, SingleValue, VectorValue}

import java.io.{DataInput, DataOutput}

object MValueCodec extends BinaryCodec[MValue] {
  override def read(in: DataInput): MValue = in.readByte() match {
    case 0     => SingleValue(FeatureName(in.readUTF()), in.readDouble())
    case 1     => VectorValue(FeatureName(in.readUTF()), DoubleArrayCodec.read(in), VectorDim(in.readVarInt()))
    case 2     => CategoryValue(FeatureName(in.readUTF()), in.readUTF(), in.readVarInt())
    case other => throw new Exception(s"cannot decode mvalue with index $other")
  }

  override def write(value: MValue, out: DataOutput): Unit = value match {
    case MValue.SingleValue(name, value) =>
      out.writeByte(0)
      out.writeUTF(name.value)
      out.writeDouble(value)
    case MValue.VectorValue(name, values, dim) =>
      out.writeByte(1)
      out.writeUTF(name.value)
      DoubleArrayCodec.write(values, out)
      out.writeVarInt(dim.dim)
    case MValue.CategoryValue(name, cat, index) =>
      out.writeByte(2)
      out.writeUTF(name.value)
      out.writeUTF(cat)
      out.writeVarInt(index)
  }
}
