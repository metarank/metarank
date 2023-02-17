package ai.metarank.fstore.file.client.mapdb

import org.mapdb.{DataInput2, DataOutput2}
import org.mapdb.serializer.{SerializerFourByte, SerializerInteger}

import java.util.Comparator

object ScalaIntSerializer extends SerializerFourByte[Int] {
  val nested                     = new SerializerInteger()
  override def pack(l: Int): Int = l

  override def unpack(l: Int): Int = l

  override def serialize(out: DataOutput2, value: Int): Unit = out.writeInt(value)

  override def deserialize(input: DataInput2, available: Int): Int = input.readInt()

  override def valueArraySearch(keys: Any, key: Int): Int =
    java.util.Arrays.binarySearch(keys.asInstanceOf[Array[Int]], key)

  override def valueArrayBinarySearch(key: Int, input: DataInput2, keysLen: Int, comparator: Comparator[_]): Int = {
    nested.valueArrayBinarySearch(key, input, keysLen, comparator)
  }
}
