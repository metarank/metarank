package ai.metarank.fstore.clickthrough

import ai.metarank.fstore.codec.StoreFormat.BinaryStoreFormat
import ai.metarank.util.TestClickthroughValues
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.nio.file.Files
import scala.util.Try

class FileTrainStoreTest extends AnyFlatSpec with Matchers {
  val ctv = TestClickthroughValues()

  val dir = Files.createTempDirectory("meta-cts")

  it should "write cts" in {
    val (store, close) = FileTrainStore
      .create(dir.toString, BinaryStoreFormat)
      .allocated
      .unsafeRunSync()
    store.put(List(ctv, ctv, ctv)).unsafeRunSync()
    store.flush().unsafeRunSync()
    close.unsafeRunSync()
  }

  it should "read cts" in {
    val (store, close) = FileTrainStore
      .create(dir.toString, BinaryStoreFormat)
      .allocated
      .unsafeRunSync()
    val read = store.getall().compile.toList.unsafeRunSync()
    read shouldBe List(ctv, ctv, ctv)
    close.unsafeRunSync()
  }

  it should "write+read cts" in {
    val (store, close) = FileTrainStore
      .create(dir.toString, BinaryStoreFormat)
      .allocated
      .unsafeRunSync()
    store.put(List(ctv, ctv, ctv)).unsafeRunSync()
    store.flush().unsafeRunSync()
    val read = store.getall().compile.toList.unsafeRunSync()
    read shouldBe List(ctv, ctv, ctv, ctv, ctv, ctv)
    close.unsafeRunSync()
  }

  it should "fail when file exists" in {
    val path = Files.createTempFile("meta-cts", ".bin")
    val cts = Try(
      FileTrainStore
        .create(path.toString, BinaryStoreFormat)
        .allocated
        .unsafeRunSync()
        ._1
    )
    cts.isFailure shouldBe true
  }
}
