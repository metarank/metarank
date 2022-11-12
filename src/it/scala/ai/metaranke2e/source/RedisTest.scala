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

  it should "connect with password" ignore {
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

  it should "connect with password and TLS" in {
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
              verify = SslVerifyMode.CA
            )
          )
        )
      )
    result.isSuccess shouldBe true
  }

}
