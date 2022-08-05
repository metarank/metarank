package ai.metarank.config

import ai.metarank.config.StateStoreConfig.{MemoryStateConfig, RedisStateConfig}
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

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

  it should "decode memory" in {
    val yaml =
      s"""type: memory""".stripMargin
    val conf = parse(yaml).flatMap(_.as[StateStoreConfig])
    conf shouldBe Right(MemoryStateConfig())
  }

}
