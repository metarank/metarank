package ai.metarank.fstore.codec


import java.io.{DataInput, DataOutput}

trait VCodec[T] {
  def encode(value: T): Array[Byte]
  def encodeDelimited(value: T, output: DataOutput): Int
  def decode(bytes: Array[Byte]): Either[Throwable, T]
  def decodeDelimited(in: DataInput): Either[Throwable, Option[T]]
}
