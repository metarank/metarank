package ai.metarank.util

import ai.metarank.util.DeduplicateByKeyTest.KV
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DeduplicateByKeyTest extends AnyFlatSpec with Matchers {
  it should "keep the first occurrence and drop later ones by key" in {
    val result = make(fs2.Stream(KV("a", 1), KV("b", 2), KV("a", 3), KV("c", 4), KV("b", 5), KV("a", 6)))
    result shouldBe List(KV("a", 1), KV("b", 2), KV("c", 4))
  }

  it should "handle empty stream" in {
    make(fs2.Stream.empty) shouldBe Nil
  }

  it should "pass through a stream without duplicates" in {
    val source = List(KV("a", 1), KV("b", 2), KV("c", 3))
    make(fs2.Stream(source*)) shouldBe source
  }

  it should "drop duplicates across concatenated streams" in {
    val first  = fs2.Stream(KV("a", 1), KV("b", 2))
    val second = fs2.Stream(KV("b", 3), KV("c", 4), KV("a", 5))
    make(first ++ second) shouldBe List(KV("a", 1), KV("b", 2), KV("c", 4))
  }

  it should "start with a fresh seen-set on every run" in {
    val stream = fs2.Stream(KV("a", 1), KV("a", 2)).through(DeduplicateByKey[KV, String](_.k))
    stream.compile.toList.unsafeRunSync() shouldBe List(KV("a", 1))
    stream.compile.toList.unsafeRunSync() shouldBe List(KV("a", 1))
  }

  def make(source: fs2.Stream[IO, KV]): List[KV] = {
    source.through(DeduplicateByKey[KV, String](_.k)).compile.toList.unsafeRunSync()
  }
}

object DeduplicateByKeyTest {
  case class KV(k: String, v: Int)
}
