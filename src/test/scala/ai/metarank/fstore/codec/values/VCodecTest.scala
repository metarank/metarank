package ai.metarank.fstore.codec.values

import ai.metarank.fstore.codec.VCodec
import com.google.common.io.ByteStreams
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait VCodecTest[T] extends AnyFlatSpec with Matchers {
  def codec: VCodec[T]
  def instance: T

  it should "roundtrip" in {
    val decoded = codec.decode(codec.encode(instance))
    decoded shouldBe Right(instance)
  }

  it should "roundtrip delimited" in {
    val bytes = ByteStreams.newDataOutput()
    codec.encodeDelimited(instance, bytes)
    val decodedDelim = codec.decodeDelimited(ByteStreams.newDataInput(bytes.toByteArray))
    decodedDelim shouldBe Right(Some(instance))
  }

  it should "handle eof" in {
    val empty = codec.decodeDelimited(ByteStreams.newDataInput(Array.emptyByteArray))
    empty shouldBe Right(None)
  }

  it should "read stream with eof" in {
    val bytes = ByteStreams.newDataOutput()
    codec.encodeDelimited(instance, bytes)
    codec.encodeDelimited(instance, bytes)
    codec.encodeDelimited(instance, bytes)
    val in = ByteStreams.newDataInput(bytes.toByteArray)
    val read = Iterator
      .continually(codec.decodeDelimited(in))
      .takeWhile {
        case Left(value)        => false
        case Right(None)        => false
        case Right(Some(value)) => true
      }
      .collect { case Right(Some(value)) =>
        value
      }
      .toList
    read shouldBe List(instance, instance, instance)
  }

}
