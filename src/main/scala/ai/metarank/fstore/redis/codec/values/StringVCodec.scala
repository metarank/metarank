package ai.metarank.fstore.redis.codec.values

import ai.metarank.fstore.redis.codec.VCodec

import java.io.{DataInput, DataOutput}
import scala.util.{Failure, Success, Try}

object StringVCodec extends VCodec[String] {
  override def decode(bytes: Array[Byte]): Either[Throwable, String] = Right(new String(bytes))

  override def encode(value: String): Array[Byte] = value.getBytes()

  override def encodeDelimited(value: String, output: DataOutput): Unit = {
    output.writeChars(value)
    output.write('\n')
  }

  override def decodeDelimited(in: DataInput): Either[Throwable, Option[String]] = {
    Try(in.readLine()) match {
      case Failure(exception) => Right(None)
      case Success(value)     => Right(Some(value))
    }
  }

}
