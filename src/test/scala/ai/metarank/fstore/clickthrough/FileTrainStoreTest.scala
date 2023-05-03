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

  it should "write+read cts" in {
    val dir = Files.createTempDirectory("meta-cts")
    val (store, close) = FileTrainStore
      .create(dir.toString, BinaryStoreFormat)
      .allocated
      .unsafeRunSync()
    store.put(List(ctv, ctv, ctv)).unsafeRunSync()
    store.flush().unsafeRunSync()
    val read = store.getall().compile.toList.unsafeRunSync()
    read shouldBe List(ctv, ctv, ctv)
    close.unsafeRunSync()
  }

  it should "append to cts" in {
    val dir = Files.createTempDirectory("meta-cts")

    val (store1, close1) = FileTrainStore
      .create(dir.toString, BinaryStoreFormat)
      .allocated
      .unsafeRunSync()
    store1.put(List(ctv, ctv, ctv)).unsafeRunSync()
    store1.flush().unsafeRunSync()
    close1.unsafeRunSync()

    val (store2, close2) = FileTrainStore
      .create(dir.toString, BinaryStoreFormat)
      .allocated
      .unsafeRunSync()
    store2.put(List(ctv, ctv, ctv)).unsafeRunSync()
    store2.flush().unsafeRunSync()
    close2.unsafeRunSync()

    val read = store2.getall().compile.toList.unsafeRunSync()
    read shouldBe List(ctv, ctv, ctv, ctv, ctv, ctv)

  }

  it should "skip file with wrong ext" in {
    val dir  = Files.createTempDirectory("meta-cts")
    val file = new File(dir.toFile, "wrong.txt")
    file.createNewFile()
    val (store, close) = FileTrainStore
      .create(dir.toString, BinaryStoreFormat)
      .allocated
      .unsafeRunSync()
    store.put(List(ctv, ctv, ctv)).unsafeRunSync()
    store.flush().unsafeRunSync()
    val read = store.getall().compile.toList.unsafeRunSync()
    read shouldBe List(ctv, ctv, ctv)
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
