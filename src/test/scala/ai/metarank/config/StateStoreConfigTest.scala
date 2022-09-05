package ai.metarank.config

import ai.metarank.config.StateStoreConfig.RedisStateConfig.{CacheConfig, DBConfig, PipelineConfig}
import ai.metarank.config.StateStoreConfig.{MemoryStateConfig, RedisStateConfig}
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class StateStoreConfigTest extends AnyFlatSpec with Matchers {
  it should "decode redis" in {
    val yaml =
      s"""
         |type: redis
         |host: localhost
         |port: 1234""".stripMargin
    val conf = parse(yaml).flatMap(_.as[StateStoreConfig])
    conf shouldBe Right(RedisStateConfig(Hostname("localhost"), Port(1234)))
  }

  it should "decode redis with cache and dboptions" in {
    val yaml =
      s"""
         |type: redis
         |host: localhost
         |port: 1234
         |db:
         |  state: 4
         |  values: 3
         |  rankings: 2
         |  hist: 1
         |  models: 0
         |cache:
         |  ttl: 24h
         |  maxSize: 1024
         |pipeline:
         |  maxSize: 123
         |  flushPeriod: 1h
         |""".stripMargin
    val conf = parse(yaml).flatMap(_.as[StateStoreConfig])
    conf shouldBe Right(
      RedisStateConfig(
        Hostname("localhost"),
        Port(1234),
        DBConfig(3, 2, 1, 0),
        CacheConfig(1024, 24.hours),
        PipelineConfig(123, 1.hour)
      )
    )
  }

  it should "decode memory" in {
    val yaml =
      s"""type: memory""".stripMargin
    val conf = parse(yaml).flatMap(_.as[StateStoreConfig])
    conf shouldBe Right(MemoryStateConfig())
  }

  it should "decode partial redis cache config" in {
    val conf = parse("ttl: 77h").flatMap(_.as[CacheConfig])
    conf shouldBe Right(CacheConfig(ttl = 77.hour))
  }

  it should "decode partial redis pipeline config" in {
    val conf = parse("flushPeriod: 77h").flatMap(_.as[PipelineConfig])
    conf shouldBe Right(PipelineConfig(flushPeriod = 77.hour))
  }

}
