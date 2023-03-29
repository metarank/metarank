package ai.metarank.fstore.codec.impl

import ai.metarank.fstore.codec.impl.CodecOps.{DataInputOps, DataOutputOps}
import ai.metarank.model.Identifier.UserId
import ai.metarank.model.{Clickthrough, EventId, Timestamp}

import java.io.{DataInput, DataOutput}

case class ClickthroughCodec(version: Int) extends BinaryCodec[Clickthrough] {
  val listItemCodec = new ListCodec(ItemIdCodec)
  val listInterCodec = new ListCodec(TypedIntCodec(version))
  val optionSessionCodec = new OptionCodec(SessionIdCodec)
  val listFieldCodec = new ListCodec(FieldCodec)


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
