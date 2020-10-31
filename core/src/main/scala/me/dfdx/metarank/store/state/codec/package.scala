package me.dfdx.metarank.store.state

import java.io.{DataInput, DataOutput}

package object codec {
  implicit val stringCodec = new Codec[String] {
    override def read(in: DataInput): String                 = in.readUTF()
    override def write(value: String, out: DataOutput): Unit = out.writeUTF(value)
  }

  implicit val stringKeyCodec = new KeyCodec[String] {
    override def write(value: String): String = value
  }
}
