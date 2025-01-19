package ai.metarank.config

import ai.metarank.config.CoreConfig.TrackingConfig
import ai.metarank.config.StateStoreConfig.{RedisCredentials, RedisStateConfig}
import ai.metarank.config.TrainConfig.RedisTrainConfig
import ai.metarank.util.Logging
import cats.effect.IO
import cats.implicits._

object ConfigEnvSubst extends Logging {
  type Subst = (Config, Map[String, String]) => IO[Config]
  val substitutions: List[Subst] = List(
    substTracking,
    substRedisCreds
  )
  def apply(config: Config, env: Map[String, String]): IO[Config] =
    substitutions.foldM(config) { case (next, subst) => subst(next, env) }

  def substRedisCreds(config: Config, env: Map[String, String]): IO[Config] =
    (env.get("METARANK_REDIS_USER"), env.get("METARANK_REDIS_PASSWORD")) match {
      case (user, Some(password)) => setCreds(config, user, password)
      case (None, None)           => IO.pure(config)
      case (Some(user), None) =>
        val msg = s"METARANK_REDIS_USER=$user is set, but METARANK_REDIS_PASSWORD is not."
        IO.raiseError(new Exception(msg))
    }

  def substTracking(config: Config, env: Map[String, String]): IO[Config] = {
    substTracking(config.core.tracking, env).map(tracking => config.copy(core = config.core.copy(tracking = tracking)))

  }

  def substTracking(track: TrackingConfig, env: Map[String, String]): IO[TrackingConfig] =
    env.get("METARANK_TRACKING") match {
      case None => IO.pure(track)
      case Some(StringBool(enabled)) =>
        info(s"using env var METARANK_TRACKING=$enabled for telemetry control") *> IO(TrackingConfig(enabled, enabled))
      case Some(other) =>
        IO.raiseError(new Exception(s"""env var METARANK_TRACKING has an unsupported value '$other'.
                                       | Expected true/1/on/enabled/false/0/off/disabled""".stripMargin))
    }

  def setCreds(config: Config, user: Option[String], password: String): IO[Config] = config match {
    case RedisState(s, t) =>
      info("picked up METARANK_REDIS_* env variables for Redis auth") *> IO {
        val auth = Some(RedisCredentials(user, password))
        config.copy(
          state = s.map(_.copy(auth = auth)).getOrElse(config.state),
          train = t.map(_.copy(auth = auth)).getOrElse(config.train)
        )
      }
    case _ =>
      IO.raiseError(
        new Exception("""METARANK_REDIS_* env vars assume that you define redis as state/train store, but
                        |it's not used. Check your config for state.type and train.type parameters. """.stripMargin)
      )
  }

  object RedisState {
    def unapply(c: Config): Option[(Option[RedisStateConfig], Option[RedisTrainConfig])] = {
      val state = c.state match {
        case r: RedisStateConfig => Some(r)
        case _                   => None
      }
      val train = c.train match {
        case r: RedisTrainConfig => Some(r)
        case _                   => None
      }
      if (state.isDefined || train.isDefined) Some((state, train)) else None
    }
  }

  object StringBool {
    def unapply(bool: String): Option[Boolean] = bool match {
      case "true" | "1" | "on" | "enabled"    => Some(true)
      case "false" | "0" | "off" | "disabled" => Some(false)
      case other                              => None
    }
  }
}
