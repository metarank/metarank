package ai.metarank.source.rest

import org.apache.flink.core.io.SimpleVersionedSerializer

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

case object RestSplitCheckpointSerializer extends SimpleVersionedSerializer[List[RestSplit]] {
  override def getVersion: Int = 1

  override def serialize(obj: List[RestSplit]): Array[Byte] = {
    val bytes = new ByteArrayOutputStream()
    val out   = new DataOutputStream(bytes)
    out.writeInt(obj.size)
    obj.foreach(RestSplitSerializer.writeSplit(out, _))
    bytes.toByteArray
  }

  override def deserialize(version: Int, serialized: Array[Byte]): List[RestSplit] = {
    val in   = new DataInputStream(new ByteArrayInputStream(serialized))
    val size = in.readInt()
    (0 until size).toList.map(_ => RestSplitSerializer.readSplit(in))
  }
}
