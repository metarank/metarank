package ai.metarank.feature.matcher

import ai.metarank.util.TextAnalyzer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TermMatcherTest extends AnyFlatSpec with Matchers {
  val matcher = TermMatcher(TextAnalyzer.english)

  it should "tokenize duplicates" in {
    val result = matcher.tokenize("greetings to hamsters!")
    result.toList shouldBe List("greet", "hamster")
  }

  it should "compute score for full match" in {
    matcher.score(Array("a", "b", "c"), Array("a", "b", "c")) shouldBe 1.0
  }

  it should "compute score for half match" in {
    matcher.score(Array("a"), Array("a", "b")) shouldBe 0.5
  }

  it should "compute score for no match" in {
    matcher.score(Array("c", "d"), Array("a", "b")) shouldBe 0.0
  }

}
