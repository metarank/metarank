package ai.metarank.fstore.redis

import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RedisStreamStoreTest extends AnyFlatSpec with Matchers with RedisTest {
  it should "read empty" in {
    val stream = RedisStreamStore("empty", pipeline, client)
    val result = stream.getall().compile.toList.unsafeRunSync()
    result shouldBe Nil
  }

  it should "write and read" in {
    val stream = RedisStreamStore("test", pipeline, client)
    stream.push(List("foo")).unsafeRunSync()
    flushPipeline().unsafeRunSync()
    stream.push(List("bar", "baz")).unsafeRunSync()
    flushPipeline().unsafeRunSync()
    val result = stream.getall().compile.toList.unsafeRunSync()
    result shouldBe List("baz", "bar", "foo")
  }

}
