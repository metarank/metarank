package me.dfdx.metarank.store.state

import java.io.{DataInput, DataOutput}

import me.dfdx.metarank.store.state.State.{Codec, KeyCodec}

object codecs {
  implicit val stringCodec = new Codec[String] {
    override def read(in: DataInput): String                 = in.readUTF()
    override def write(value: String, out: DataOutput): Unit = out.writeUTF(value)
  }

  implicit val stringKeyCodec = new KeyCodec[String] {
    override def read(in: String): String     = in
    override def write(value: String): String = value
  }
}
