package ai.metarank.config

import ai.metarank.config.StateStoreConfig.{DBConfig, MemoryStateConfig, RedisStateConfig}
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
    conf shouldBe Right(RedisStateConfig(Hostname("localhost"), Port(1234), DBConfig()))
  }

  it should "decode redis with custom db layout" in {
    val yaml =
      s"""
         |type: redis
         |host: localhost
         |port: 1234
         |db:
         |  state: 1
         |  fields: 2
         |  clickthroughs: 3
         |  features: 4""".stripMargin
    val conf = parse(yaml).flatMap(_.as[StateStoreConfig])
    conf shouldBe Right(RedisStateConfig(Hostname("localhost"), Port(1234), DBConfig(1, 2, 3, 4)))
  }

  it should "decode memory" in {
    val yaml =
      s"""type: memory""".stripMargin
    val conf = parse(yaml).flatMap(_.as[StateStoreConfig])
    conf shouldBe Right(MemoryStateConfig(DBConfig()))
  }

}
