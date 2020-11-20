package me.dfdx.metarank.config

import better.files.Resource
import me.dfdx.metarank.aggregation.Aggregation
import me.dfdx.metarank.config.Config.{ConfigSyntaxError, YamlDecodingError}
import me.dfdx.metarank.model.Featurespace
import me.dfdx.metarank.store.HeapStore
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfigLoadingTest extends AnyFlatSpec with Matchers {
  lazy val yaml = Resource.my.getAsString("/config/config.valid.yml")
  it should "parse valid config" in {
    val result = Config.load(yaml)
    val br     = 1
    result.map(_.featurespace.map(_.id.name)) shouldBe Right(List("demo"))
  }

  it should "fail on config with no keyspaces" in {
    val yaml   = Resource.my.getAsString("/config/config.invalid.nokeyspaces.yml")
    val result = Config.load(yaml)
    result should matchPattern { case Left(ConfigSyntaxError(_, _)) =>
    }
  }

  it should "fail on config with broken yaml" in {
    val yaml   = Resource.my.getAsString("/config/config.invalid.invalidyaml.yml")
    val result = Config.load(yaml)
    result should matchPattern { case Left(YamlDecodingError(_, _)) =>
    }
  }
}
