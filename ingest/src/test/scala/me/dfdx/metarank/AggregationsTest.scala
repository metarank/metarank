package me.dfdx.metarank

import better.files.Resource
import me.dfdx.metarank.config.Config
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AggregationsTest extends AnyFlatSpec with Matchers {
  it should "load aggs from config" in {
    val yaml   = Resource.my.getAsString("/config/config.valid.yml")
    val result = Config.load(yaml)
    val aggs   = result.map(conf => Aggregations.fromConfig(conf.featurespace.head))
    aggs should matchPattern { case Right(Aggregations(a)) =>
    }
  }
}
