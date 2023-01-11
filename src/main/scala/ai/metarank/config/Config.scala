package ai.metarank.config

import ai.metarank.config.StateStoreConfig.{MemoryStateConfig, RedisStateConfig}
import ai.metarank.model.FeatureSchema
import ai.metarank.util.Logging
import cats.data.{NonEmptyList, NonEmptyMap}
import cats.effect.IO
import io.circe.Decoder
import io.circe.yaml.parser.{parse => parseYaml}

case class Config(
    core: CoreConfig,
    api: ApiConfig,
    state: StateStoreConfig,
    train: TrainConfig,
    input: Option[InputConfig],
    features: NonEmptyList[FeatureSchema],
    models: Map[String, ModelConfig]
)

object Config extends Logging {

  implicit val configDecoder: Decoder[Config] = Decoder
    .instance(c =>
      for {
        coreOption  <- c.downField("core").as[Option[CoreConfig]]
        apiOption   <- c.downField("api").as[Option[ApiConfig]]
        stateOption <- c.downField("state").as[Option[StateStoreConfig]]
        trainOption <- c.downField("train").as[Option[TrainConfig]]
        inputOption <- c.downField("input").as[Option[InputConfig]]
        features    <- c.downField("features").as[NonEmptyList[FeatureSchema]]
        models      <- c.downField("models").as[Map[String, ModelConfig]]
      } yield {
        val api   = get(apiOption, ApiConfig(), "api")
        val state = get(stateOption, MemoryStateConfig(), "state")
        val train = get(trainOption, TrainConfig.fromState(state), "train")
        val core  = coreOption.getOrElse(CoreConfig())
        Config(core, api, state, train, inputOption, features, models)
      }
    )
    .ensure(ConfigValidations.checkFeatureModelReferences)

  def get[T](opt: Option[T], default: T, name: String) = opt match {
    case Some(value) => value
    case None =>
      logger.info(s"$name conf block is not defined: using default $default")
      default
  }

  def load(contents: String, env: Map[String, String]): IO[Config] = {
    for {
      yaml     <- IO.fromEither(parseYaml(contents))
      decoded  <- IO.fromEither(yaml.as[Config])
      envSubst <- ConfigEnvSubst(decoded, env)
      _        <- logConfig(envSubst)
    } yield {
      envSubst
    }
  }

  def logConfig(conf: Config): IO[Unit] = IO {
    val stateType = conf.state match {
      case RedisStateConfig(host, port, db, cache, pipeline, _, _, _, _) => s"redis://${host.value}:${port.value}"
      case MemoryStateConfig()                                           => "memory"
      case StateStoreConfig.FileStateConfig(path, format, backend)       => s"file://$path"
    }
    val features = conf.features.map(_.name.value).toList.mkString("[", ",", "]")
    val models   = conf.models.keys.mkString("[", ",", "]")
    logger.info(s"Loaded config file, state=$stateType, features=$features, models=$models")
  }

}
