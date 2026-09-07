package ai.metaranke2e.ct

import ai.metarank.config.TrainConfig.CompressionType.{GzipCompressionType, ZstdCompressionType}
import ai.metarank.config.TrainConfig.{CompressionType, S3TrainConfig}
import ai.metarank.fstore.clickthrough.S3TrainStore
import ai.metarank.util.TestClickthroughValues
import cats.effect.IO
import cats.effect.syntax.all.*
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.model.PutObjectRequest

import scala.util.Random

class S3TrainStoreTest extends AnyFlatSpec with Matchers {
  val ct = TestClickthroughValues()

  def config(
      prefix: String,
      compress: CompressionType = GzipCompressionType,
      partSizeEvents: Int = 1024
  ): S3TrainConfig = S3TrainConfig(
    bucket = "bucket",
    region = "eu-west-1",
    prefix = prefix,
    endpoint = Some("http://localhost:4566"),
    compress = compress,
    partSizeEvents = partSizeEvents
  )

  def makeStore(conf: S3TrainConfig): S3TrainStore =
    S3TrainStore.create(conf).allocated.map(_._1).unsafeRunSync()

  lazy val store = makeStore(config(s"test_${Random.nextInt(100000)}"))

  it should "write+read cts" in {
    val events = List.fill(1000)(ct)
    store.put(events).unsafeRunSync()
    store.flush().unsafeRunSync()
    val read = store.getall().compile.toList.unsafeRunSync()
    read shouldBe events
  }

  it should "not corrupt records on concurrent writes" in {
    val store   = makeStore(config(s"test_${Random.nextInt(100000)}", partSizeEvents = 100))
    val events  = (0 until 1000).map(i => TestClickthroughValues(List(s"a$i", s"b$i"))).toList
    val batches = events.grouped(10).toList
    batches.parTraverse_(batch => store.put(batch)).unsafeRunSync()
    store.flush().unsafeRunSync()
    val read = store.getall().compile.toList.unsafeRunSync()
    read should contain theSameElementsAs events
  }

  it should "read parts written with a different compression config" in {
    val prefix    = s"test_${Random.nextInt(100000)}"
    val gzipStore = makeStore(config(prefix, compress = GzipCompressionType))
    val events    = List.fill(100)(ct)
    gzipStore.put(events).unsafeRunSync()
    gzipStore.flush().unsafeRunSync()
    val zstdStore = makeStore(config(prefix, compress = ZstdCompressionType))
    val read      = zstdStore.getall().compile.toList.unsafeRunSync()
    read shouldBe events
  }

  it should "list keys past the 1000-key page limit" in {
    val prefix = s"test_${Random.nextInt(100000)}"
    val store  = makeStore(config(prefix))
    val count  = 1001
    (0 until count).toList
      .parTraverseN(16)(i =>
        IO.fromCompletableFuture(
          IO(
            store.client.putObject(
              PutObjectRequest.builder().bucket("bucket").key(s"$prefix/part_$i.bin").build(),
              AsyncRequestBody.fromString("x")
            )
          )
        )
      )
      .unsafeRunSync()

    store.listKeys().unsafeRunSync().size shouldBe count
  }
}
