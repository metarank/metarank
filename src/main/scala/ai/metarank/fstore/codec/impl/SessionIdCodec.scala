package ai.metarank.fstore.codec.impl

import ai.metarank.model.Identifier.SessionId

import java.io.{DataInput, DataOutput}

object SessionIdCodec extends BinaryCodec[SessionId] {
  override def read(in: DataInput): SessionId                 = SessionId(in.readUTF())
  override def write(value: SessionId, out: DataOutput): Unit = out.writeUTF(value.value)
}
