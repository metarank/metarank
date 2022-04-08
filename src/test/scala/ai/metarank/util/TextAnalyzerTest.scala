package ai.metarank.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TextAnalyzerTest extends AnyFlatSpec with Matchers {
  it should "split simple lines" in {
    TextAnalyzer.icu.split("hello, world!").toList shouldBe List("hello", "world")
  }

  it should "split english" in {
    TextAnalyzer.english.split("mashed potatoes").toList shouldBe List("mash", "potato")
  }
}
