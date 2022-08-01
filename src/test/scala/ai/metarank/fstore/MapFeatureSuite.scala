package ai.metarank.fstore

import ai.metarank.model.Feature.MapFeature.MapConfig
import ai.metarank.model.FeatureValue.MapValue
import ai.metarank.model.Key.{FeatureName, Scope}
import ai.metarank.model.Scalar.SString
import ai.metarank.model.Write.PutTuple
import ai.metarank.util.TestKey

import scala.concurrent.duration._

trait MapFeatureSuite extends FeatureSuite[PutTuple] {
  val config = MapConfig(scope = Scope("b"), FeatureName("counter"), 1.day)
  val k      = TestKey(config, id = "p11")

  it should "write-read" in {
    val result = write(List(PutTuple(k, now, "foo", Some(SString("bar")))))
    result shouldBe Some(MapValue(k, now, Map("foo" -> SString("bar"))))
  }

  it should "update" in {
    val result = write(List(PutTuple(k, now, "foo", Some(SString("baz")))))
    result shouldBe Some(MapValue(k, now, Map("foo" -> SString("baz"))))
  }

  it should "remove" in {
    val result = write(List(PutTuple(k, now, "foo", None)))
    result shouldBe None
  }
}
