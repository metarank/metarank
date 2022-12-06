package ai.metarank.fstore.clickthrough

import ai.metarank.fstore.codec.StoreFormat.BinaryStoreFormat
import ai.metarank.util.TestClickthroughValues
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.Files

class FileClickthroughStoreTest extends AnyFlatSpec with Matchers {
  val ctv = TestClickthroughValues()

  it should "write+read cts" in {
    val path = Files.createTempFile("cts", ".dat").toString
    val (store, close) = FileClickthroughStore
      .create(path, BinaryStoreFormat)
      .allocated
      .unsafeRunSync()
    store.put(List(ctv, ctv, ctv)).unsafeRunSync()

    val read = store.getall().compile.toList.unsafeRunSync()
    read shouldBe List(ctv, ctv, ctv)
    close.unsafeRunSync()
  }
}
