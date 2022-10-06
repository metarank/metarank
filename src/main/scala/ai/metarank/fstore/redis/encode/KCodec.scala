package ai.metarank.fstore.redis.encode

import ai.metarank.model.Key

trait KCodec[T] {
  def encode(prefix: String, value: T): String
  def decode(str: String): Either[Exception, T]
}
