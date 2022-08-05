package ai.metarank.fstore

import ai.metarank.model.Feature.StatsEstimator.StatsEstimatorConfig
import ai.metarank.model.FeatureValue.NumStatsValue
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.Write.PutStatSample
import ai.metarank.util.TestKey

trait StatsEstimatorSuite extends FeatureSuite[PutStatSample] {
  val config =
    StatsEstimatorConfig(scope = ItemScopeType, FeatureName("f1"), 100, 1, List(50, 90))

  it should "measure a 1-100 range" in {
    val k      = TestKey(config, id = "p10")
    val puts   = for { i <- 0 until 100 } yield { PutStatSample(k, now, i.toDouble) }
    val result = write(puts.toList).get.asInstanceOf[NumStatsValue]
    result.min should be >= 0.0
    result.max should be <= 100.0
    result.quantiles.values.toList.forall(_ > 1) shouldBe true
  }

  it should "measure a 1-1000 range" in {
    val k      = TestKey(config, id = "p11")
    val puts   = for { i <- 0 until 1000 } yield { PutStatSample(k, now, i.toDouble) }
    val result = write(puts.toList).get.asInstanceOf[NumStatsValue]
    result.quantiles.values.toList.exists(_ > 10) shouldBe true
  }
}
