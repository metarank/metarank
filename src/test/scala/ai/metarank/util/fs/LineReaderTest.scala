package ai.metarank.util.fs

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util

class LineReaderTest extends AnyFlatSpec with Matchers {
  it should "read multiple lines" in {
    LineReader.lines("foo\nbar".getBytes) shouldBe List("foo", "bar")
  }

  it should "read empty" in {
    LineReader.lines(Array.emptyByteArray) shouldBe Nil
  }
}
