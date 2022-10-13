package ai.metarank.fstore.redis.codec.impl
import java.io.{DataInput, DataOutput}

class OptionCodec[T](c: BinaryCodec[T]) extends BinaryCodec[Option[T]] {
  override def read(in: DataInput): Option[T] = in.readBoolean() match {
    case true  => Some(c.read(in))
    case false => None
  }

  override def write(value: Option[T], out: DataOutput): Unit = value match {
    case Some(value) =>
      out.writeBoolean(true)
      c.write(value, out)
    case None => out.writeBoolean(false)
  }
}
