package me.dfdx.metarank.aggregation.generator

import org.scalacheck.Gen

object IncrementingTimestampsGenerator {
  def apply(): Gen[List[Int]] =
    for {
      start <- Gen.posNum[Int]
      list  <- Gen.nonEmptyListOf(Gen.chooseNum[Int](0, 3))
    } yield {
      var acc = start
      val result = for {
        item <- list
      } yield {
        acc += item
        acc
      }
      result
    }
}
