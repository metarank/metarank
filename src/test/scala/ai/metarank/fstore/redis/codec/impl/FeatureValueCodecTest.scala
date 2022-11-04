package ai.metarank.fstore.redis.codec.impl

import ai.metarank.fstore.codec.impl.FeatureValueCodec
import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import ai.metarank.model.FeatureValue.PeriodicCounterValue.PeriodicValue
import ai.metarank.model.FeatureValue.{
  BoundedListValue,
  CounterValue,
  FrequencyValue,
  MapValue,
  NumStatsValue,
  PeriodicCounterValue,
  ScalarValue
}
import ai.metarank.model.Identifier.UserId
import ai.metarank.model.{Key, Timestamp}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scalar.SString
import ai.metarank.model.Scope.UserScope
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FeatureValueCodecTest extends AnyFlatSpec with Matchers with BinCodecTest {
  val k  = Key(UserScope(UserId("u1")), FeatureName("foo"))
  val ts = Timestamp.now

  it should "do scalars" in {
    roundtrip(FeatureValueCodec, ScalarValue(k, ts, SString("foo")))
  }

  it should "do counters" in {
    roundtrip(FeatureValueCodec, CounterValue(k, ts, 1L))
  }

  it should "do numstats" in {
    roundtrip(FeatureValueCodec, NumStatsValue(k, ts, 1.0, 2.0, Map(1 -> 1.0)))
  }

  it should "do maps" in {
    roundtrip(FeatureValueCodec, MapValue(k, ts, Map("foo" -> SString("bar"))))
  }

  it should "do periodic counters" in {
    roundtrip(FeatureValueCodec, PeriodicCounterValue(k, ts, List(PeriodicValue(ts, ts, 1, 1L))))
  }

  it should "do freqs" in {
    roundtrip(FeatureValueCodec, FrequencyValue(k, ts, Map("foo" -> 1.0)))
  }

  it should "do lists" in {
    roundtrip(FeatureValueCodec, BoundedListValue(k, ts, List(TimeValue(ts, SString("foo")))))
  }
}
