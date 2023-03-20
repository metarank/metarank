package ai.metarank.fstore.codec.impl

import ai.metarank.fstore.codec.impl.TrainValuesCodec.ClickthroughValuesCodec.FieldCodec
import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.Dimension.{SingleDim, VectorDim}
import ai.metarank.model.Field.{BooleanField, NumberField, NumberListField, StringField, StringListField}
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.{CategoryValue, SingleValue, VectorValue}
import ai.metarank.model.TrainValues.{ClickthroughValues, ItemValues, UserValues}
import ai.metarank.model.{Clickthrough, EventId, Field, ItemValue, MValue, Timestamp, TrainValues}

import java.io.{DataInput, DataOutput}

object TrainValuesCodec extends BinaryCodec[TrainValues] {
  import CodecOps._
  val VERSION        = 2
  val listFieldCodec = new ListCodec(FieldCodec)

  override def read(in: DataInput): TrainValues = {
    val version = in.readByte()
    version match {
      case 1 =>
        ClickthroughValuesCodec.read(in)
      case 2 =>
        val sub = in.readByte()
        sub.toInt match {
          case 0     => ClickthroughValuesCodec.read(in)
          case 1     => ItemValuesCodec.read(in)
          case 2     => UserValuesCodec.read(in)
          case other => throw new IllegalStateException(s"train sub-item index $other not expected")
        }

    }
  }

  override def write(value: TrainValues, out: DataOutput): Unit = {
    out.writeByte(VERSION)
    value match {
      case c: ClickthroughValues =>
        out.writeByte(0)
        ClickthroughValuesCodec.write(c, out)
      case i: TrainValues.ItemValues =>
        out.writeByte(1)
        ItemValuesCodec.write(i, out)
      case u: TrainValues.UserValues =>
        out.writeByte(2)
        UserValuesCodec.write(u, out)
    }
  }

  object ItemValuesCodec extends BinaryCodec[ItemValues] {
    override def read(in: DataInput): ItemValues = {
      val id     = in.readUTF()
      val item   = in.readUTF()
      val ts     = in.readLong()
      val fields = listFieldCodec.read(in)
      ItemValues(EventId(id), ItemId(item), Timestamp(ts), fields)
    }
    override def write(value: ItemValues, out: DataOutput): Unit = {
      out.writeUTF(value.id.value)
      out.writeUTF(value.item.value)
      out.writeLong(value.timestamp.ts)
      listFieldCodec.write(value.fields, out)
    }
  }

  object UserValuesCodec extends BinaryCodec[UserValues] {
    override def read(in: DataInput): UserValues = {
      val id     = in.readUTF()
      val user   = in.readUTF()
      val ts     = in.readLong()
      val fields = listFieldCodec.read(in)
      UserValues(EventId(id), UserId(user), Timestamp(ts), fields)
    }
    override def write(value: UserValues, out: DataOutput): Unit = {
      out.writeUTF(value.id.value)
      out.writeUTF(value.user.value)
      out.writeLong(value.timestamp.ts)
      listFieldCodec.write(value.fields, out)
    }
  }

  object ClickthroughValuesCodec extends BinaryCodec[ClickthroughValues] {
    import CodecOps._

    val listItemValueCodec = new ListCodec(ItemValueCodec)

    override def read(in: DataInput): ClickthroughValues = ClickthroughValues(
      ct = ClickthroughCodec.read(in),
      values = listItemValueCodec.read(in)
    )

    override def write(value: ClickthroughValues, out: DataOutput): Unit = {
      ClickthroughCodec.write(value.ct, out)
      listItemValueCodec.write(value.values, out)
    }

    object ClickthroughCodec extends BinaryCodec[Clickthrough] {
      val listItemCodec      = new ListCodec(ItemIdCodec)
      val listInterCodec     = new ListCodec(TypedIntCodec)
      val optionSessionCodec = new OptionCodec(SessionIdCodec)

      override def read(in: DataInput): Clickthrough = {
        Clickthrough(
          id = EventId(in.readUTF()),
          ts = Timestamp(in.readVarLong()),
          user = Option.when(in.readBoolean())(UserId(in.readUTF())),
          session = optionSessionCodec.read(in),
          items = listItemCodec.read(in),
          interactions = listInterCodec.read(in),
          rankingFields = listFieldCodec.read(in)
        )
      }

      override def write(value: Clickthrough, out: DataOutput): Unit = {
        out.writeUTF(value.id.value)
        out.writeVarLong(value.ts.ts)
        value.user match {
          case Some(value) =>
            out.writeBoolean(true)
            out.writeUTF(value.value)
          case None => out.writeBoolean(false)
        }
        optionSessionCodec.write(value.session, out)
        listItemCodec.write(value.items, out)
        listInterCodec.write(value.interactions, out)
        listFieldCodec.write(value.rankingFields, out)
      }
    }

    object ItemIdCodec extends BinaryCodec[ItemId] {
      override def read(in: DataInput): ItemId                 = ItemId(in.readUTF())
      override def write(value: ItemId, out: DataOutput): Unit = out.writeUTF(value.value)
    }

    object SessionIdCodec extends BinaryCodec[SessionId] {
      override def read(in: DataInput): SessionId                 = SessionId(in.readUTF())
      override def write(value: SessionId, out: DataOutput): Unit = out.writeUTF(value.value)
    }

    object TypedIntCodec extends BinaryCodec[TypedInteraction] {
      override def read(in: DataInput): TypedInteraction =
        TypedInteraction(
          item = ItemId(in.readUTF()),
          tpe = in.readUTF()
        )

      override def write(value: TypedInteraction, out: DataOutput): Unit = {
        out.writeUTF(value.item.value)
        out.writeUTF(value.tpe)
      }
    }

    object ItemValueCodec extends BinaryCodec[ItemValue] {
      val listMvalCodec = new ListCodec(MValueCodec)
      override def read(in: DataInput): ItemValue = {
        ItemValue(
          id = ItemIdCodec.read(in),
          values = listMvalCodec.read(in)
        )
      }

      override def write(value: ItemValue, out: DataOutput): Unit = {
        ItemIdCodec.write(value.id, out)
        listMvalCodec.write(value.values, out)
      }
    }

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

    object FieldCodec extends BinaryCodec[Field] {
      override def read(in: DataInput): Field = in.readByte() match {
        case 0     => StringField(in.readUTF(), in.readUTF())
        case 1     => BooleanField(in.readUTF(), in.readBoolean())
        case 2     => NumberField(in.readUTF(), in.readDouble())
        case 3     => StringListField(in.readUTF(), (0 until in.readInt()).map(_ => in.readUTF()).toList)
        case 4     => NumberListField(in.readUTF(), (0 until in.readInt()).map(_ => in.readDouble()).toArray)
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
          out.writeInt(value.size)
          value.foreach(out.writeDouble)

      }
    }
  }

}
