package ai.metarank.mode.bootstrap

import ai.metarank.model.Field.{BooleanField, NumberField, NumberListField, StringField, StringListField}
import ai.metarank.model.FieldId.{ItemFieldId, UserFieldId}
import ai.metarank.model.FieldUpdate
import ai.metarank.model.Identifier.{ItemId, UserId}
import io.findify.featury.flink.format.BulkCodec
import io.findify.featury.model.Key.Tenant

import java.io.{DataInputStream, DataOutputStream, InputStream, OutputStream}

case object FieldUpdateCodec extends BulkCodec[FieldUpdate] {
  override def ext: String                        = ".bin"
  override def bucket(value: FieldUpdate): String = s"${value.id.tenant.value}/${value.id.field}"

  override def read(stream: InputStream): Option[FieldUpdate] = {
    if (stream.available() > 0) {
      val view = new DataInputStream(stream)
      val idOption = view.readByte() match {
        case 0 => Some(ItemFieldId(Tenant(view.readUTF()), ItemId(view.readUTF()), view.readUTF()))
        case 1 => Some(UserFieldId(Tenant(view.readUTF()), UserId(view.readUTF()), view.readUTF()))
        case _ => None
      }
      val valueOption = view.readByte() match {
        case 0 => Some(StringField(view.readUTF(), view.readUTF()))
        case 1 => Some(BooleanField(view.readUTF(), view.readBoolean()))
        case 2 => Some(NumberField(view.readUTF(), view.readDouble()))
        case 3 => Some(StringListField(view.readUTF(), readList(view, _.readUTF())))
        case 4 => Some(NumberListField(view.readUTF(), readList(view, _.readDouble())))
        case _ => None
      }
      for {
        id    <- idOption
        value <- valueOption
      } yield {
        FieldUpdate(id, value)
      }
    } else {
      None
    }
  }

  def readList[T](view: DataInputStream, f: DataInputStream => T): List[T] = {
    val size = view.readInt()
    (0 until size).map(_ => f(view)).toList
  }

  override def write(value: FieldUpdate, stream: OutputStream): Unit = {
    val view = new DataOutputStream(stream)
    value.id match {
      case ItemFieldId(tenant, item, field) =>
        view.writeByte(0)
        view.writeUTF(tenant.value)
        view.writeUTF(item.value)
        view.writeUTF(field)
      case UserFieldId(tenant, user, field) =>
        view.writeByte(1)
        view.writeUTF(tenant.value)
        view.writeUTF(user.value)
        view.writeUTF(field)
    }
    value.value match {
      case StringField(name, value) =>
        view.writeByte(0)
        view.writeUTF(name)
        view.writeUTF(value)
      case BooleanField(name, value) =>
        view.writeByte(1)
        view.writeUTF(name)
        view.writeBoolean(value)
      case NumberField(name, value) =>
        view.writeByte(2)
        view.writeUTF(name)
        view.writeDouble(value)
      case StringListField(name, value) =>
        view.writeByte(3)
        view.writeUTF(name)
        view.writeInt(value.size)
        value.foreach(view.writeUTF)
      case NumberListField(name, value) =>
        view.writeByte(4)
        view.writeUTF(name)
        view.writeInt(value.size)
        value.foreach(view.writeDouble)
    }
  }
}
