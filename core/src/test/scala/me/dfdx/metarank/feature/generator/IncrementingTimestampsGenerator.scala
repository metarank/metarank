package me.dfdx.metarank.feature.generator

import org.scalacheck.Gen

object IncrementingTimestampsGenerator {
  def apply(): Gen[List[Int]] =
    for {
      length <- Gen.chooseNum[Int](1, 90)
      start  <- Gen.posNum[Int]
      list   <- Gen.listOfN(length, Gen.chooseNum[Int](0, 3)).map(_.toArray)
    } yield {
      var acc    = start
      val result = new Array[Int](length)
      var i      = 0
      while (i < length) {
        acc += list(i)
        result(i) = acc
        i += 1
      }
      result.toList
    }
}
