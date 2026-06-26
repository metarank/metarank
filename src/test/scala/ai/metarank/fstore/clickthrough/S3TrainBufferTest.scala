package ai.metarank.fstore.clickthrough

import ai.metarank.config.TrainConfig.CompressionType
import ai.metarank.config.TrainConfig.CompressionType.{GzipCompressionType, NoCompressionType, ZstdCompressionType}
import ai.metarank.config.TrainConfig.S3TrainConfig
import ai.metarank.fstore.codec.StoreFormat.BinaryStoreFormat
import ai.metarank.model.TrainValues
import ai.metarank.util.TestClickthroughValues
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayInputStream

class S3TrainBufferTest extends AnyFlatSpec with Matchers {
  val codec = BinaryStoreFormat.ctv
  val ct    = TestClickthroughValues()

  // Accumulate across many small put() calls so the read path has to span chunk boundaries,
  // then compress + decode the whole part and confirm it round-trips.
  def roundtrip(compress: CompressionType): Unit = {
    val events: List[TrainValues] = List.fill(1000)(ct)
    val buffer                    = events.grouped(7).foldLeft(S3TrainStore.Buffer(compress, codec))((buf, b) => buf.put(b.toList))
    buffer.eventCount shouldBe events.size
    val bytes = buffer.toByteArray()
    val conf  = S3TrainConfig(bucket = "b", region = "eu-west-1", prefix = "p", compress = compress)
    val read = S3TrainStore
      .create(conf)
      .use(store => store.read(new ByteArrayInputStream(bytes)).compile.toList)
      .unsafeRunSync()
    read shouldBe events
  }

  it should "round-trip with gzip" in roundtrip(GzipCompressionType)
  it should "round-trip with zstd" in roundtrip(ZstdCompressionType)
  it should "round-trip with no compression" in roundtrip(NoCompressionType)

  it should "treat an empty put as a no-op" in {
    val buffer = S3TrainStore.Buffer(GzipCompressionType, codec)
    buffer.nonEmpty shouldBe false
    buffer.put(Nil) shouldBe buffer
  }
}
