package ai.metarank.fstore.redis.codec

import ai.metarank.fstore.redis.codec.impl.BinaryCodec
import com.github.luben.zstd.{ZstdInputStream, ZstdOutputStream}
import com.google.common.io.ByteStreams
import io.circe.{Json, Codec => CirceCodec}
import org.typelevel.jawn.AsyncParser
import org.typelevel.jawn.AsyncParser.ValueStream

import java.io.{BufferedInputStream, BufferedOutputStream, ByteArrayInputStream, ByteArrayOutputStream, DataInput, DataInputStream, DataOutput, DataOutputStream, OutputStream}
import java.nio.CharBuffer
import scala.util.{Failure, Success, Try}

trait VCodec[T] {
  def encode(value: T): Array[Byte]
  def encodeDelimited(value: T, output: DataOutput): Unit
  def decode(bytes: Array[Byte]): Either[Throwable, T]
  def decodeDelimited(in: DataInput): Either[Throwable, Option[T]]
}

object VCodec {
  import io.circe.syntax._
  import io.circe.parser.{decode => cdecode}

  def json[T](implicit jc: CirceCodec[T]): VCodec[T] = new VCodec[T] {
    override def encode(value: T): Array[Byte] = value.asJson.noSpaces.getBytes
    override def encodeDelimited(value: T, output: DataOutput): Unit = {
      output.write(encode(value))
      output.write('\n')
    }
    override def decode(bytes: Array[Byte]): Either[Throwable, T] = cdecode[T](new String(bytes))

    override def decodeDelimited(in: DataInput): Either[Throwable, Option[T]] = {
      var buf = CharBuffer.allocate(32)
      Try(in.readChar()) match {
        case Failure(exception) => ???
        case Success(value) => ???
      }
    }
  }

  def binary[T](codec: BinaryCodec[T]) = new VCodec[T] {
    override def decode(bytes: Array[Byte]): Either[Throwable, T] = {
      val in = ByteStreams.newDataInput(bytes)
      Try(codec.read(in)) match {
        case Failure(exception) => Left(exception)
        case Success(value)     => Right(value)
      }
    }

    override def encode(value: T): Array[Byte] = {
      val out = ByteStreams.newDataOutput()
      codec.write(value, out)
      out.toByteArray
    }

    override def encodeDelimited(value: T, output: DataOutput): Unit = {
      val bytes = encode(value)
      output.writeInt(bytes.length)
      output.write(bytes)
    }
  }

  def bincomp[T](codec: BinaryCodec[T]) = new VCodec[T] {
    override def decode(bytes: Array[Byte]): Either[Throwable, T] = {
      val stream = new ByteArrayInputStream(bytes)
      val in     = new DataInputStream(new BufferedInputStream(new ZstdInputStream(stream), 1024 * 32))
      Try(codec.read(in)) match {
        case Failure(exception) => Left(exception)
        case Success(value)     => Right(value)
      }
    }

    override def encode(value: T): Array[Byte] = {
      val bytes    = new ByteArrayOutputStream()
      val buffered = new BufferedOutputStream(new ZstdOutputStream(bytes, 3), 1024 * 16)
      val out      = new DataOutputStream(buffered)
      codec.write(value, out)
      buffered.flush()
      bytes.toByteArray
    }

    override def encodeDelimited(value: T, output: DataOutput): Unit = {
      val bytes = encode(value)
      output.writeInt(bytes.length)
      output.write(bytes)
    }
  }

  val string = new VCodec[String] {
    override def decode(bytes: Array[Byte]): Either[Throwable, String]    = Right(new String(bytes))
    override def encode(value: String): Array[Byte]                       = value.getBytes()
    override def encodeDelimited(value: String, output: DataOutput): Unit = ???
  }
}
