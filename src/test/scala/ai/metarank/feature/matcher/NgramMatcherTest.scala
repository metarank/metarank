package ai.metarank.feature.matcher

import ai.metarank.util.TextAnalyzer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class NgramMatcherTest extends AnyFlatSpec with Matchers {
  val ngram = NgramMatcher(3, TextAnalyzer.whitespace)

  it should "tokenize duplicates" in {
    val result = ngram.tokenize("fooba foo")
    result.toList shouldBe List("foo", "oba", "oob")
  }

  it should "not crash on all unique ngrams" in {
    val result = ngram.tokenize("foobar")
    result.toList shouldBe List("bar", "foo", "oba", "oob")
  }

  it should "compute score for full match" in {
    ngram.score(Array("a", "b", "c"), Array("a", "b", "c")) shouldBe 1.0
  }

  it should "compute score for half match" in {
    ngram.score(Array("a"), Array("a", "b")) shouldBe 0.5
  }

  it should "compute score for no match" in {
    ngram.score(Array("c", "d"), Array("a", "b")) shouldBe 0.0
  }

}
