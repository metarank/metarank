package ai.metaranke2e.source

import ai.metarank.config.StateStoreConfig.{RedisCredentials, RedisTLS}
import ai.metarank.config.StateStoreConfig.RedisStateConfig.PipelineConfig
import ai.metarank.fstore.redis.client.RedisClient
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}
import io.lettuce.core.SslVerifyMode
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.File
import scala.util.Try

class RedisTest extends AnyFlatSpec with Matchers {

  it should "connect with password" in {
    val result =
      Try(
        RedisClient.createUnsafe(
          "localhost",
          16379,
          0,
          PipelineConfig(),
          Ref.of[IO, Int](0).unsafeRunSync(),
          Some(RedisCredentials(None, "test")),
          None
        )
      )
    result.isSuccess shouldBe true
  }

  it should "connect with password and TLS (verify=full)" in {
    val result =
      Try(
        RedisClient.createUnsafe(
          "localhost",
          26379,
          0,
          PipelineConfig(),
          Ref.of[IO, Int](0).unsafeRunSync(),
          Some(RedisCredentials(None, "password123")),
          Some(
            RedisTLS(
              ca = Some(new File(".github/tls/redistls.crt")),
              verify = SslVerifyMode.FULL
            )
          )
        )
      )
    result.isSuccess shouldBe true
  }

  it should "connect with password and TLS (verify=off, no CA cert)" in {
    val result =
      Try(
        RedisClient.createUnsafe(
          "localhost",
          26379,
          0,
          PipelineConfig(),
          Ref.of[IO, Int](0).unsafeRunSync(),
          Some(RedisCredentials(None, "password123")),
          Some(
            RedisTLS(
              ca = None,
              verify = SslVerifyMode.NONE
            )
          )
        )
      )
    result.isSuccess shouldBe true
  }

}
