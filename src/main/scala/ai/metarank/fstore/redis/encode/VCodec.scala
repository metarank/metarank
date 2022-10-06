package ai.metarank.fstore.redis.encode

import io.circe.Codec

trait VCodec[T] {
  def encode(value: T): Array[Byte]
  def decode(bytes: Array[Byte]): Either[Throwable, T]
}

object VCodec {
  import io.circe.syntax._
  import io.circe.parser.{decode => cdecode}

  def json[T](implicit jc: Codec[T]) = new VCodec[T] {
    override def encode(value: T): Array[Byte]                    = value.asJson.noSpaces.getBytes
    override def decode(bytes: Array[Byte]): Either[Throwable, T] = cdecode[T](new String(bytes))
  }
}
