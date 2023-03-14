package ai.metaranke2e.ct

import ai.metarank.config.TrainConfig.S3TrainConfig
import ai.metarank.fstore.clickthrough.S3TrainStore
import ai.metarank.util.TestClickthroughValues
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class S3TrainStoreTest extends AnyFlatSpec with Matchers {
  val ct = TestClickthroughValues()
  lazy val store = S3TrainStore
    .create(
      S3TrainConfig(
        bucket = "bucket",
        region = "eu-west-1",
        prefix = s"test_${Random.nextInt(100000)}",
        endpoint = Some("http://localhost:4566")
      )
    )
    .allocated
    .map(_._1)
    .unsafeRunSync()

  it should "write+read cts" in {
    val events = List.fill(1000)(ct)
    store.put(events).unsafeRunSync()
    store.flush().unsafeRunSync()
    val read = store.getall().compile.toList.unsafeRunSync()
    read shouldBe events
  }
}
