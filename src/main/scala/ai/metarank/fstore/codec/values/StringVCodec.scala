package ai.metarank.fstore.codec.values

import ai.metarank.fstore.codec.VCodec
import java.io.{DataInput, DataOutput}
import scala.util.{Failure, Success, Try}

object StringVCodec extends VCodec[String] {
  override def decode(bytes: Array[Byte]): Either[Throwable, String] = Right(new String(bytes))

  override def encode(value: String): Array[Byte] = value.getBytes()

  override def encodeDelimited(value: String, output: DataOutput): Unit = {
    output.write(value.getBytes())
    output.write('\n')
  }

  override def decodeDelimited(in: DataInput): Either[Throwable, Option[String]] = {
    val line = in.readLine()
    if (line == null) {
      Right(None)
    } else {
      Right(Some(line))
    }
  }

}
