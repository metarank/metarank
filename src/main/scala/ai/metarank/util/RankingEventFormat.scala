package ai.metarank.util

import ai.metarank.model.Event.{RankItem, RankingEvent}
import ai.metarank.model.Field.{BooleanField, NumberField, NumberListField, StringField, StringListField}
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.{EventId, Field, Timestamp}
import cats.data.NonEmptyList

import java.io.{DataInputStream, DataOutputStream}

object RankingEventFormat {
  def read(stream: DataInputStream): RankingEvent = {
    val id = EventId(stream.readUTF())
    val ts = Timestamp(stream.readLong())
    val user = stream.readBoolean() match {
      case true  => Some(UserId(stream.readUTF()))
      case false => None
    }
    val session = stream.readBoolean() match {
      case true  => Some(SessionId(stream.readUTF()))
      case false => None
    }
    val fields = (0 until stream.readInt()).map(_ => readField(stream)).toList
    val items = (0 until stream.readInt()).map(_ => {
      RankItem(
        id = ItemId(stream.readUTF()),
        fields = (0 until stream.readInt()).map(_ => readField(stream)).toList
      )
    })
    RankingEvent(
      id = id,
      timestamp = ts,
      user = user,
      session = session,
      fields = fields,
      items = NonEmptyList.fromListUnsafe(items.toList)
    )
  }
  def write(request: RankingEvent, stream: DataOutputStream): Unit = {
    stream.writeUTF(request.id.value)
    stream.writeLong(request.timestamp.ts)
    request.user match {
      case Some(value) =>
        stream.writeBoolean(true)
        stream.writeUTF(value.value)
      case None => stream.writeBoolean(false)
    }
    request.session match {
      case Some(value) =>
        stream.writeBoolean(true)
        stream.writeUTF(value.value)
      case None => stream.writeBoolean(false)
    }
    stream.writeInt(request.fields.size)
    request.fields.foreach(f => writeField(f, stream))
    stream.writeInt(request.items.size)
    request.items.toList.foreach(item => {
      stream.writeUTF(item.id.value)
      stream.writeInt(item.fields.size)
      item.fields.foreach(f => writeField(f, stream))
    })
  }

  private def writeField(field: Field, stream: DataOutputStream): Unit = field match {
    case Field.StringField(name, value) =>
      stream.writeByte(0)
      stream.writeUTF(name)
      stream.writeUTF(value)
    case Field.BooleanField(name, value) =>
      stream.writeByte(1)
      stream.writeUTF(name)
      stream.writeBoolean(value)
    case Field.NumberField(name, value) =>
      stream.writeByte(2)
      stream.writeUTF(name)
      stream.writeDouble(value)
    case Field.StringListField(name, value) =>
      stream.writeByte(3)
      stream.writeUTF(name)
      stream.writeInt(value.size)
      value.foreach(s => stream.writeUTF(s))
    case Field.NumberListField(name, value) =>
      stream.writeByte(4)
      stream.writeUTF(name)
      stream.writeInt(value.length)
      value.foreach(d => stream.writeDouble(d))
  }

  private def readField(stream: DataInputStream): Field = stream.readByte() match {
    case 0 => StringField(stream.readUTF(), stream.readUTF())
    case 1 => BooleanField(stream.readUTF(), stream.readBoolean())
    case 2 => NumberField(stream.readUTF(), stream.readDouble())
    case 3 => StringListField(stream.readUTF(), (0 until stream.readInt()).map(_ => stream.readUTF()).toList)
    case 4 => NumberListField(stream.readUTF(), (0 until stream.readInt()).map(_ => stream.readDouble()).toArray)

  }
}
