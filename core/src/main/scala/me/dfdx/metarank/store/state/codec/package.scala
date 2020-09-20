package me.dfdx.metarank.store.state

import java.nio.charset.StandardCharsets

package object codec {
  implicit val stringCodec = new Codec[String] {
    override def read(in: Array[Byte]): String     = new String(in, StandardCharsets.UTF_8)
    override def write(value: String): Array[Byte] = value.getBytes(StandardCharsets.UTF_8)
  }
}
