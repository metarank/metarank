package ai.metarank.fstore

import ai.metarank.model.Feature.FreqEstimator.FreqEstimatorConfig
import ai.metarank.model.FeatureValue.FrequencyValue
import ai.metarank.model.Key.{FeatureName, Scope}
import ai.metarank.model.Timestamp
import ai.metarank.model.Write.PutFreqSample
import ai.metarank.util.TestKey

import scala.util.Random

trait FreqEstimatorSuite extends FeatureSuite[PutFreqSample] {
  val config: FreqEstimatorConfig =
    FreqEstimatorConfig(scope = Scope("b"), name = FeatureName("f1"), 100, 1)

  it should "sample freqs for 100 items" in {
    val k = TestKey(config, id = "f10")
    val puts = for { i <- 0 until 100 } yield {
      PutFreqSample(k, Timestamp.now, "p" + math.round(math.abs(Random.nextGaussian() * 10.0)).toString)
    }
    val result = write(puts.toList).collect { case f: FrequencyValue => f }.get
    result.values.values.sum shouldBe 1.0 +- 0.001
    result.values.getOrElse("p1", 0.0) should be > 0.01
  }
}
