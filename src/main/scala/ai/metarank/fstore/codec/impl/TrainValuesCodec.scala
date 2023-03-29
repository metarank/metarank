package ai.metarank.fstore.codec.impl

import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.TrainValues.{ClickthroughValues, ItemValues, UserValues}
import ai.metarank.model.{Clickthrough, EventId, Field, ItemValue, MValue, Timestamp, TrainValues}

import java.io.{DataInput, DataOutput}

object TrainValuesCodec extends BinaryCodec[TrainValues] {
  import CodecOps._
  val VERSION        = 3
  val listFieldCodec = new ListCodec(FieldCodec)

  val ctv2 = ClickthroughValuesCodec(2)
  val ctv3 = ClickthroughValuesCodec(3)
  override def read(in: DataInput): TrainValues = {
    val version = in.readByte()
    version match {
      case 1 =>
        ctv2.read(in)
      case 2 | 3 =>
        val sub = in.readByte()
        sub.toInt match {
          case 0     => if (version == 2) ctv2.read(in) else ctv3.read(in)
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
        ctv3.write(c, out)
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

  case class ClickthroughValuesCodec(version: Int) extends BinaryCodec[ClickthroughValues] {
    import CodecOps._

    val ctvCodec           = ClickthroughCodec(version)
    val listItemValueCodec = new ListCodec(ItemValueCodec)

    override def read(in: DataInput): ClickthroughValues = ClickthroughValues(
      ct = ctvCodec.read(in),
      values = listItemValueCodec.read(in)
    )

    override def write(value: ClickthroughValues, out: DataOutput): Unit = {
      ctvCodec.write(value.ct, out)
      listItemValueCodec.write(value.values, out)
    }

  }

}
