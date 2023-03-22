package ai.metarank.fstore.codec.impl

import ai.metarank.model.Identifier.ItemId

import java.io.{DataInput, DataOutput}

object ItemIdCodec extends BinaryCodec[ItemId] {
  override def read(in: DataInput): ItemId = ItemId(in.readUTF())
  override def write(value: ItemId, out: DataOutput): Unit = out.writeUTF(value.value)
}
