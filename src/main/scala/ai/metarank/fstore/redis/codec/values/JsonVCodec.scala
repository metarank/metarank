package ai.metarank.fstore.redis.codec.values

import ai.metarank.fstore.redis.codec.VCodec
import io.circe.Codec
import io.circe.syntax._
import io.circe.parser.{decode => cdecode}

import java.io.{DataInput, DataOutput}
import scala.util.{Failure, Success, Try}

case class JsonVCodec[T](implicit c: Codec[T]) extends VCodec[T] {
  override def encode(value: T): Array[Byte] = value.asJson.noSpaces.getBytes

  override def encodeDelimited(value: T, output: DataOutput): Unit = {
    output.write(encode(value))
    output.write('\n')
  }

  override def decode(bytes: Array[Byte]): Either[Throwable, T] = cdecode[T](new String(bytes))

  override def decodeDelimited(in: DataInput): Either[Throwable, Option[T]] = {
    Try(in.readLine()) match {
      case Success(line) => cdecode[T](line).map(Option.apply)
      case Failure(ex) => Right(None)
    }
  }

}
