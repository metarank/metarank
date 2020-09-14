package me.dfdx.metarank.store.state

import java.io.{ByteArrayOutputStream, DataInput, DataOutput, DataOutputStream}

sealed trait State {
  def name: String
}

object State {
  case class ValueState[T](name: String)(implicit val codec: Codec[T])                    extends State
  case class MapState[K, V](name: String)(implicit val kc: KeyCodec[K], val vc: Codec[V]) extends State

  trait Codec[T] {
    def read(in: DataInput): T
    def write(value: T, out: DataOutput): Unit
    def write(value: T): Array[Byte] = {
      val buffer = new ByteArrayOutputStream()
      write(value, new DataOutputStream(buffer))
      buffer.toByteArray
    }
  }

  trait KeyCodec[T] {
    def read(in: String): T
    def write(value: T): String
  }
}
