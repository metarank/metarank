package ai.metarank.util

import ai.metarank.util.SortedGroupByTest.KV
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SortedGroupByTest extends AnyFlatSpec with Matchers {
  it should "group by" in {
    val result = make[KV, String](List(KV("a", 1), KV("a", 2), KV("b", 3), KV("b", 4), KV("c", 5)), _.k)
    result shouldBe List(List(KV("a", 1), KV("a", 2)), List(KV("b", 3), KV("b", 4)), List(KV("c", 5)))
  }

  it should "handle empty list" in {
    make[KV, String](Nil, _.k) shouldBe Nil
  }

  it should "handle no groups" in {
    val result = make[KV, String](List(KV("a", 1), KV("b", 3), KV("c", 5)), _.k)
    result shouldBe List(List(KV("a", 1)), List(KV("b", 3)), List(KV("c", 5)))
  }

  def make[T, K](source: List[T], by: T => K): List[List[T]] = {
    fs2
      .Stream(source: _*)
      .through(SortedGroupBy.groupBy(by))
      .compile
      .toList
      .unsafeRunSync()
  }
}

object SortedGroupByTest {
  case class KV(k: String, v: Int)
}
