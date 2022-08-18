package ai.metarank.fstore.redis

import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RedisKVStoreTest extends AnyFlatSpec with Matchers with RedisTest {
  lazy val kv = RedisKVStore[String, String](client)

  it should "get empty" in {
    kv.get(List("a", "b")).unsafeRunSync() shouldBe Map.empty
  }

  it should "write-read" in {
    kv.put(Map("foo" -> "bar")).unsafeRunSync()
    kv.get(List("foo")).unsafeRunSync() shouldBe Map("foo" -> "bar")
  }
}
