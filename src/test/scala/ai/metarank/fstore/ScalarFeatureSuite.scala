package ai.metarank.fstore

import ai.metarank.model.Feature.ScalarFeature
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scalar.SString
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.State.ScalarState
import ai.metarank.model.Write.Put
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global

import scala.concurrent.duration._

trait ScalarFeatureSuite extends FeatureSuite[Put, ScalarConfig, ScalarFeature] {
  val config = ScalarConfig(scope = ItemScopeType, FeatureName("counter"), 1.day)

  it should "write and read" in {
    val key    = TestKey(config, id = "p11")
    val result = write(List(Put(key, now, SString("foo"))))
    result shouldBe Some(ScalarValue(key, now, SString("foo")))
  }

  it should "update and read" in {
    val key    = TestKey(config, id = "p12")
    val put1   = Put(key, now, SString("1"))
    val put2   = Put(key, now, SString("2"))
    val result = write(List(put1, put2))
    result shouldBe Some(ScalarValue(key, now, put2.value))
  }


//  it should "accept bulk upload" in {
//    val state = ScalarState(Key(ItemScope(ItemId("p13")), FeatureName("counter")), SString("bar"))
//    feature().to(fs2.Stream(state)).unsafeRunSync()
//    val back = feature().computeValue(state.key, now).unsafeRunSync()
//    back shouldBe Some(ScalarValue(state.key, now, state.value))
//  }
}
