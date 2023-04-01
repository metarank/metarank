package ai.metarank.util

import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LocalCacheTest extends AnyFlatSpec with Matchers {
  it should "init with default dir" in {
    val cache = LocalCache.create().unsafeRunSync()
  }

  it should "cache a file and read it" in {
    val cache = LocalCache.create().unsafeRunSync()
    cache.put("test", "test.bin", "test".getBytes()).unsafeRunSync()
    val back = cache.getIfExists("test", "test.bin").unsafeRunSync()
    back.map(new String(_)) shouldBe Some("test")
  }

  it should "load empty on not found" in {
    val cache = LocalCache.create().unsafeRunSync()
    val back  = cache.getIfExists("test", "404.bin").unsafeRunSync()
    back shouldBe None
  }
}
