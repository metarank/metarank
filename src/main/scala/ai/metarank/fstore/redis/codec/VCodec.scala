package ai.metarank.fstore.redis.codec

import ai.metarank.fstore.redis.codec.impl.BinaryCodec
import com.github.luben.zstd.{ZstdInputStream, ZstdOutputStream}
import com.google.common.io.ByteStreams
import io.circe.{Json, Codec => CirceCodec}
import org.typelevel.jawn.AsyncParser
import org.typelevel.jawn.AsyncParser.ValueStream

import java.io.{
  BufferedInputStream,
  BufferedOutputStream,
  ByteArrayInputStream,
  ByteArrayOutputStream,
  DataInput,
  DataInputStream,
  DataOutput,
  DataOutputStream,
  OutputStream
}
import java.nio.CharBuffer
import java.util.Scanner
import scala.util.{Failure, Success, Try}

trait VCodec[T] {
  def encode(value: T): Array[Byte]
  def encodeDelimited(value: T, output: DataOutput): Unit
  def decode(bytes: Array[Byte]): Either[Throwable, T]
  def decodeDelimited(in: DataInput): Either[Throwable, Option[T]]
}

