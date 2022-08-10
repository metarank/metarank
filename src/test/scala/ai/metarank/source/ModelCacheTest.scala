package ai.metarank.source

import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.ModelKey
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.Env
import ai.metarank.rank.NoopModel.NoopScorer
import ai.metarank.util.TestFeatureMapping
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._

import scala.util.Try

class ModelCacheTest extends AnyFlatSpec with Matchers {
  it should "complain on missing models" in {
    val mc     = ModelCache(Persistence.blackhole())
    val result = Try(mc.get(Env.default, "404").unsafeRunSync())
    result.isFailure shouldBe true
  }

  it should "load model from store" in {
    import ai.metarank.rank.Model._
    val store = MemPersistence(TestFeatureMapping().schema)
    store.models.put(Map(ModelKey(Env.default, "test") -> NoopScorer)).unsafeRunSync()
    val mc = ModelCache(store)
    mc.get(Env.default, "test").unsafeRunSync() shouldBe NoopScorer
    mc.cache.getIfPresent(ModelKey(Env.default, "test")).isDefined shouldBe true
  }
}
