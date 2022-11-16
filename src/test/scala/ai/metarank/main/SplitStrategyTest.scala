package ai.metarank.main

import ai.metarank.main.command.train.SplitStrategy
import ai.metarank.main.command.train.SplitStrategy.RandomSplit
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SplitStrategyTest extends AnyFlatSpec with Matchers {
  it should "parse inputs" in {
    SplitStrategy.parse("random=10%") shouldBe Right(RandomSplit(10))
    SplitStrategy.parse("random") shouldBe Right(RandomSplit(80))
  }
}
