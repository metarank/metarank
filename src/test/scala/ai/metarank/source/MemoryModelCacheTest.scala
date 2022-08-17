package ai.metarank.source

import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.rank.NoopModel.NoopScorer
import ai.metarank.source.ModelCache.MemoryModelCache
import ai.metarank.util.TestFeatureMapping
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._

import scala.util.Try

class MemoryModelCacheTest extends AnyFlatSpec with Matchers {
  it should "complain on missing models" in {
    val mc     = MemoryModelCache(Persistence.blackhole())
    val result = Try(mc.get("404").unsafeRunSync())
    result.isFailure shouldBe true
  }

  it should "load model from store" in {
    import ai.metarank.rank.Model._
    val store = MemPersistence(TestFeatureMapping().schema)
    store.models.put(Map(ModelName("test") -> NoopScorer)).unsafeRunSync()
    val mc = MemoryModelCache(store)
    mc.get("test").unsafeRunSync() shouldBe NoopScorer
    mc.cache.getIfPresent("test").isDefined shouldBe true
  }
}
