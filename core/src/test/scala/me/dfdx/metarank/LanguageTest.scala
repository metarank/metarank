package me.dfdx.metarank

import me.dfdx.metarank.model.Language
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LanguageTest extends AnyFlatSpec with Matchers {
  it should "split text to terms" in {
    val tokens = Language.English.tokenize("quick fox jumps over a lazy dog")
    tokens shouldBe List("quick", "fox", "jump", "over", "lazi", "dog")
  }
}
