package ai.metarank.fstore.file.client.mapdb

import org.mapdb.{DataInput2, DataOutput2}
import org.mapdb.serializer.SerializerFourByte

import java.util
import java.util.Arrays

object ScalaFloatSerializer extends SerializerFourByte[Float] {
  override def pack(l: Float): Int = java.lang.Float.floatToIntBits(l)

  override def unpack(l: Int): Float = java.lang.Float.intBitsToFloat(l)

  override def serialize(out: DataOutput2, value: Float): Unit = out.writeFloat(value)

  override def deserialize(input: DataInput2, available: Int): Float = input.readFloat()

  override def valueArraySearch(keys: Any, key: Float): Int = util.Arrays.binarySearch(valueArrayToArray(keys), key)
}
