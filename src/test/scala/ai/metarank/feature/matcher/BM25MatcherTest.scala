package ai.metarank.feature.matcher

import ai.metarank.feature.matcher.BM25Matcher.TermFreqDic
import ai.metarank.util.TextAnalyzer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BM25MatcherTest extends AnyFlatSpec with Matchers {
  lazy val tf = TermFreqDic(
    docs = 3,
    avgdl = 3.0,
    docfreq = Map("foo" -> 1, "bar" -> 2, "baz" -> 3)
  )
  lazy val matcher = BM25Matcher(TextAnalyzer.english, tf)

  it should "for high freq query be low" in {
    matcher.score(Array("baz"), Array("bar", "baz")) shouldBe 0.15 +- 0.01
  }

  it should "for low freq query be high" in {
    matcher.score(Array("foo"), Array("foo")) shouldBe 1.34 +- 0.01
  }
}
