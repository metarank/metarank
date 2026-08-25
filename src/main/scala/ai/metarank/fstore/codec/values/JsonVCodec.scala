package ai.metarank.fstore.codec.values

import ai.metarank.fstore.codec.VCodec
import io.circe.Codec
import io.circe.syntax.*
import io.circe.parser.{decode as cdecode}

import java.io.{DataInput, DataOutput}

case class JsonVCodec[T](c: Codec[T]) extends VCodec[T] {
  override def encode(value: T): Array[Byte] = value.asJson(using c).noSpaces.getBytes

  override def encodeDelimited(value: T, output: DataOutput): Int = {
    val bytes = encode(value)
    output.write(bytes)
    output.write('\n')
    bytes.length + 1
  }

  override def decode(bytes: Array[Byte]): Either[Throwable, T] = cdecode[T](new String(bytes))(using c)

  override def decodeDelimited(in: DataInput): Either[Throwable, Option[T]] = {
    val line = in.readLine()
    if (line == null) {
      Right(None)
    } else {
      cdecode[T](line)(using c).map(Option.apply)
    }
  }

}
