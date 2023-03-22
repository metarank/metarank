package ai.metarank.fstore.codec.impl

import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.Identifier.ItemId

import java.io.{DataInput, DataOutput}

case class TypedIntCodec(version: Int) extends BinaryCodec[TypedInteraction] {
  override def read(in: DataInput): TypedInteraction =
    TypedInteraction(
      item = ItemId(in.readUTF()),
      tpe = in.readUTF(),
      rel = version match {
        case 3 => if (in.readBoolean()) Some(in.readInt()) else None
        case _ => None
      }
    )

  override def write(value: TypedInteraction, out: DataOutput): Unit = {
    out.writeUTF(value.item.value)
    out.writeUTF(value.tpe)
    value.rel match {
      case Some(value) =>
        out.writeBoolean(true)
        out.writeInt(value)
      case None =>
        out.writeBoolean(false)
    }
  }
}
