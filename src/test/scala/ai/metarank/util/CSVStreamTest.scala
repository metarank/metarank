package ai.metarank.util

import better.files.File
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CSVStreamTest extends AnyFlatSpec with Matchers {
  it should "read stream from a file" in {
    val file = File.newTemporaryFile("csv", ".csv")
    file.writeText("1,2,3\n4,5,6")
    val result = CSVStream.fromFile(file.toString(), ',', 0).compile.toList.unsafeRunSync()
    result.map(_.toList) shouldBe List(List("1", "2", "3"), List("4", "5", "6"))
    file.delete()
  }
}
