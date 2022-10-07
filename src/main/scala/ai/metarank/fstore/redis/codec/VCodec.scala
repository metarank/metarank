package ai.metarank.fstore.redis.codec

import ai.metarank.fstore.redis.codec.impl.BinaryCodec
import com.google.common.io.ByteStreams
import io.circe.{Codec => CirceCodec}

import scala.util.{Failure, Success, Try}

trait VCodec[T] {
  def encode(value: T): Array[Byte]
  def decode(bytes: Array[Byte]): Either[Throwable, T]
}

object VCodec {
  import io.circe.syntax._
  import io.circe.parser.{decode => cdecode}

  def json[T](implicit jc: CirceCodec[T]): VCodec[T] = new VCodec[T] {
    override def encode(value: T): Array[Byte]                    = value.asJson.noSpaces.getBytes
    override def decode(bytes: Array[Byte]): Either[Throwable, T] = cdecode[T](new String(bytes))
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
  }

  val string = new VCodec[String] {
    override def decode(bytes: Array[Byte]): Either[Throwable, String] = Right(new String(bytes))
    override def encode(value: String): Array[Byte]                    = value.getBytes()
  }
}
