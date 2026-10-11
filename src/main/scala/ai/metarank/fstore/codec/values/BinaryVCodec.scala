package ai.metarank.fstore.codec.values

import ai.metarank.fstore.codec.VCodec
import ai.metarank.fstore.codec.impl.BinaryCodec
import com.github.luben.zstd.{RecyclingBufferPool, ZstdInputStreamNoFinalizer, ZstdOutputStream}
import ai.metarank.util.Logging

import java.io.{
  BufferedOutputStream,
  ByteArrayInputStream,
  ByteArrayOutputStream,
  DataInput,
  DataInputStream,
  DataOutput,
  DataOutputStream
}
import scala.util.{Failure, Success, Try}

case class BinaryVCodec[T](compress: Boolean, codec: BinaryCodec[T]) extends VCodec[T] with Logging {
  override def decode(bytes: Array[Byte]): Either[Throwable, T] = {
    val result = for {
      raw   <- if (compress) Try(decompress(bytes)) else Success(bytes)
      value <- Try(codec.read(new DataInputStream(new ByteArrayInputStream(raw))))
    } yield value
    result.toEither
  }

  // Pooled buffers: a zstd stream allocates ~128 KB, a record is a few KB
  private def decompress(bytes: Array[Byte]): Array[Byte] = {
    val zstd = new ZstdInputStreamNoFinalizer(new ByteArrayInputStream(bytes), RecyclingBufferPool.INSTANCE)
    try zstd.readAllBytes()
    finally zstd.close()
  }

  override def encode(value: T): Array[Byte] = {
    val bytes = new ByteArrayOutputStream()
    val stream = if (compress) {
      new DataOutputStream(new BufferedOutputStream(new ZstdOutputStream(bytes, 1), 1024 * 16))
    } else {
      new DataOutputStream(bytes)
    }
    codec.write(value, stream)
    stream.flush()
    stream.close()
    bytes.toByteArray
  }

  override def encodeDelimited(value: T, output: DataOutput): Int = {
    val bytes = encode(value)
    output.writeInt(bytes.length)
    output.write(bytes)
    bytes.length
  }

  override def decodeDelimited(in: DataInput): Either[Throwable, Option[T]] = {
    Try(in.readInt()) match {
      case Success(size) if size < 0 =>
        logger.warn(s"corrupted stream: record size=$size is negative")
        Right(None)
      case Success(size) =>
        val buf = new Array[Byte](size)
        Try(in.readFully(buf)) match {
          case Success(_) => decode(buf).map(Option.apply)
          case Failure(ex) =>
            logger.warn(s"truncated stream: expected record of $size bytes, got EOF ($ex)")
            Right(None)
        }
      case Failure(_) => Right(None)
    }
  }
}
