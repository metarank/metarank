package ai.metarank.fstore


import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Key.{FeatureName, Scope}
import ai.metarank.model.Scalar.SString
import ai.metarank.model.Write.Put
import ai.metarank.util.TestKey

import scala.concurrent.duration._

trait ScalarFeatureSuite extends FeatureSuite[Put] {
  val config = ScalarConfig(scope = Scope("b"), FeatureName("counter"), 1.day)

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
}
