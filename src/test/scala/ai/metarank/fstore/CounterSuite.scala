package ai.metarank.fstore

import ai.metarank.model.Feature.Counter.CounterConfig
import ai.metarank.model.FeatureValue.CounterValue
import ai.metarank.model.Key.{FeatureName, Scope}
import ai.metarank.model.Write.Increment
import ai.metarank.util.TestKey

import scala.concurrent.duration._
import scala.util.Random

trait CounterSuite extends FeatureSuite[Increment] {
  val config = CounterConfig(Scope("g1"), FeatureName("c1"))

  it should "increment once" in {
    val key      = TestKey(config, id = "p11")
    val result   = write(List(Increment(key, now, 1)))
    val expected = Some(CounterValue(key, now, 1L))
    result shouldBe expected
  }

  it should "inc-dec multiple times" in {
    val key        = TestKey(config, id = "p12")
    val increments = (0 until 10).map(i => Increment(key, now.plus(i.seconds), Random.nextInt(100))).toList
    val result     = write(increments)
    result shouldBe Some(CounterValue(key, increments.map(_.ts).maxBy(_.ts), increments.map(_.inc).sum))
  }

}
