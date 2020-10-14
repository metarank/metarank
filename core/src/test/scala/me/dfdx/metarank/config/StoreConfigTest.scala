package me.dfdx.metarank.config

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser._
import me.dfdx.metarank.config.StoreConfig.{HeapStoreConfig, NullStoreConfig, RedisStoreConfig}

class StoreConfigTest extends AnyFlatSpec with Matchers {
  it should "decode null config" in {
    val yaml = """type: "null" """
    parse(yaml).flatMap(_.as[StoreConfig]) shouldBe Right(NullStoreConfig())
  }

  it should "decode memory config" in {
    val yaml = """type: "heap" """
    parse(yaml).flatMap(_.as[StoreConfig]) shouldBe Right(HeapStoreConfig())
  }

  it should "decode redis config" in {
    val yaml =
      """type: "redis"
        |host: "localhost"
        |port: 1234""".stripMargin
    parse(yaml).flatMap(_.as[StoreConfig]) shouldBe Right(RedisStoreConfig("localhost", 1234))
  }
}
