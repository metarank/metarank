package ai.metarank.fstore.codec.impl

import ai.metarank.model.Field
import ai.metarank.model.Field.{BooleanField, NumberField, NumberListField, StringField, StringListField}

import java.io.{DataInput, DataOutput}

object FieldCodec extends BinaryCodec[Field] {
  override def read(in: DataInput): Field = in.readByte() match {
    case 0 => StringField(in.readUTF(), in.readUTF())
    case 1 => BooleanField(in.readUTF(), in.readBoolean())
    case 2 => NumberField(in.readUTF(), in.readDouble())
    case 3 => StringListField(in.readUTF(), (0 until in.readInt()).map(_ => in.readUTF()).toList)
    case 4 => NumberListField(in.readUTF(), (0 until in.readInt()).map(_ => in.readDouble()).toArray)
    case other => throw new Exception(s"cannot decode type index $other")
  }

  override def write(value: Field, out: DataOutput): Unit = value match {
    case Field.StringField(name, value) =>
      out.writeByte(0)
      out.writeUTF(name)
      out.writeUTF(value)
    case Field.BooleanField(name, value) =>
      out.writeByte(1)
      out.writeUTF(name)
      out.writeBoolean(value)
    case Field.NumberField(name, value) =>
      out.writeByte(2)
      out.writeUTF(name)
      out.writeDouble(value)
    case Field.StringListField(name, value) =>
      out.writeByte(3)
      out.writeUTF(name)
      out.writeInt(value.size)
      value.foreach(out.writeUTF)
    case Field.NumberListField(name, value) =>
      out.writeByte(4)
      out.writeUTF(name)
      out.writeInt(value.length)
      value.foreach(out.writeDouble)
  }
}
