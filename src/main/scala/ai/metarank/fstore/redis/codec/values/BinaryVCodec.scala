package ai.metarank.fstore.redis.codec.values

import ai.metarank.fstore.redis.codec.VCodec
import ai.metarank.fstore.redis.codec.impl.BinaryCodec
import com.github.luben.zstd.{ZstdInputStream, ZstdOutputStream}

import java.io.{
  BufferedInputStream,
  BufferedOutputStream,
  ByteArrayInputStream,
  ByteArrayOutputStream,
  DataInput,
  DataInputStream,
  DataOutput,
  DataOutputStream
}
import scala.util.{Failure, Success, Try}

case class BinaryVCodec[T](compress: Boolean, codec: BinaryCodec[T]) extends VCodec[T] {
  override def decode(bytes: Array[Byte]): Either[Throwable, T] = {
    val stream = if (compress) {
      new DataInputStream(new BufferedInputStream(new ZstdInputStream(new ByteArrayInputStream(bytes)), 1024 * 32))
    } else {
      new DataInputStream(new ByteArrayInputStream(bytes))
    }
    val result = Try(codec.read(stream)) match {
      case Failure(exception) => Left(exception)
      case Success(value)     => Right(value)
    }
    stream.close()
    result
  }

  override def encode(value: T): Array[Byte] = {
    val bytes = new ByteArrayOutputStream()
    val stream = if (compress) {
      new DataOutputStream(new BufferedOutputStream(new ZstdOutputStream(bytes, 3), 1024 * 16))
    } else {
      new DataOutputStream(bytes)
    }
    codec.write(value, stream)
    stream.flush()
    bytes.toByteArray
  }

  override def encodeDelimited(value: T, output: DataOutput): Unit = {
    val bytes = encode(value)
    output.writeInt(bytes.length)
    output.write(bytes)
  }

  override def decodeDelimited(in: DataInput): Either[Throwable, Option[T]] = {
    val size = in.readInt()
    val buf  = new Array[Byte](size)
    Try(in.readFully(buf)) match {
      case Success(_)  => decode(buf).map(Option.apply)
      case Failure(ex) => Right(None)
    }
  }

}
