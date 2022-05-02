package ai.metarank.source.rest

import org.apache.flink.core.io.SimpleVersionedSerializer

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

case object RestSplitSerializer extends SimpleVersionedSerializer[RestSplit] {
  override def getVersion: Int = 1

  override def serialize(obj: RestSplit): Array[Byte] = {
    val bytes = new ByteArrayOutputStream()
    val out   = new DataOutputStream(bytes)
    writeSplit(out, obj)
    bytes.toByteArray
  }

  override def deserialize(version: Int, serialized: Array[Byte]): RestSplit = {
    val in = new DataInputStream(new ByteArrayInputStream(serialized))
    readSplit(in)
  }

  def writeSplit(stream: DataOutputStream, obj: RestSplit) = {
    stream.writeUTF(obj.splitId)
    obj.limit match {
      case None => stream.writeBoolean(false)
      case Some(lim) =>
        stream.writeBoolean(true)
        stream.writeLong(lim)
    }
  }

  def readSplit(stream: DataInputStream): RestSplit = {
    val splitId = stream.readUTF()
    stream.readBoolean() match {
      case false => RestSplit(splitId, None)
      case true =>
        val limit = stream.readLong()
        RestSplit(splitId, Some(limit))
    }
  }
}
