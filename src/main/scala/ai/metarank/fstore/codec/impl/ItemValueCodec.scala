package ai.metarank.fstore.codec.impl

import ai.metarank.model.ItemValue

import java.io.{DataInput, DataOutput}

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
