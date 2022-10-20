package ai.metaranke2e.source

import ai.metarank.config.StateStoreConfig.RedisCredentials
import ai.metarank.config.StateStoreConfig.RedisStateConfig.PipelineConfig
import ai.metarank.fstore.redis.client.RedisClient
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Try

class RedisAuthTest extends AnyFlatSpec with Matchers {

  it should "connect with password" in {
    val result =
      Try(
        RedisClient.createUnsafe(
          "localhost",
          16379,
          0,
          PipelineConfig(),
          Ref.of[IO, Int](0).unsafeRunSync(),
          Some(RedisCredentials(None, "test"))
        )
      )
    result.isSuccess shouldBe true
  }

}
