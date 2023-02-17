package ai.metarank.fstore.codec

import ai.metarank.model.Key

trait KCodec[T] {
  def encode(prefix: String, value: T): String
  def encodeNoPrefix(value: T): String
  def decode(str: String): Either[Throwable, T]
  def decodeNoPrefix(str: String): Either[Throwable, T]
}

object KCodec {
  def wrap[T](from: String => T, to: T => String): KCodec[T] = new KCodec[T] {
    override def encode(prefix: String, value: T): String = s"$prefix/${to(value)}"

    override def encodeNoPrefix(value: T): String                  = to(value)
    override def decodeNoPrefix(str: String): Either[Throwable, T] = Right(from(str))

    override def decode(str: String): Either[Throwable, T] = str.split('/').toList match {
      case _ :: value :: Nil => Right(from(value))
      case other             => Left(new Exception(s"cannot decode string $other"))
    }
  }
}
