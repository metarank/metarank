package ai.metarank.config

import ai.metarank.config.CoreConfig.TrackingConfig
import ai.metarank.config.StateStoreConfig.{RedisCredentials, RedisStateConfig}
import ai.metarank.config.TrainConfig.RedisTrainConfig
import ai.metarank.util.TestConfig
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfigEnvSubstTest extends AnyFlatSpec with Matchers {
  val conf = TestConfig()

  it should "override tracking" in {
    val trackingEnabled = conf.copy(core = conf.core.copy(tracking = TrackingConfig(analytics = true, errors = true)))
    val result          = ConfigEnvSubst(trackingEnabled, Map("METARANK_TRACKING" -> "false")).unsafeRunSync()
    result.core.tracking shouldBe TrackingConfig(analytics = false, errors = false)
  }

  it should "set redis user/password when config.state.redis.auth is unset" in {
    val redis = conf.copy(
      state = RedisStateConfig(Hostname("a"), Port(111)),
      train = RedisTrainConfig(Hostname("a"), Port(111))
    )
    credsAreDefined(redis)
  }

  it should "override pre-set creds" in {
    val redis = conf.copy(
      state = RedisStateConfig(Hostname("a"), Port(111), auth = Some(RedisCredentials(Some("bob"), "xxx"))),
      train = RedisTrainConfig(Hostname("a"), Port(111), auth = Some(RedisCredentials(Some("bob"), "xxx")))
    )
    credsAreDefined(redis)
  }

  def credsAreDefined(conf: Config) = {
    val envs   = Map("METARANK_REDIS_USER" -> "alice", "METARANK_REDIS_PASSWORD" -> "secret")
    val result = ConfigEnvSubst(conf, envs).unsafeRunSync()
    result.state should matchPattern {
      case RedisStateConfig(_, _, _, _, _, _, Some(RedisCredentials(Some("alice"), "secret")), _, _) =>
    }
    result.train should matchPattern {
      case RedisTrainConfig(_, _, _, _, _, _, Some(RedisCredentials(Some("alice"), "secret")), _, _, _) =>
    }
  }
}
