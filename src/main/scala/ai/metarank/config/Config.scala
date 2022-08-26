package ai.metarank.config

import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.StateStoreConfig.{MemoryStateConfig, RedisStateConfig}
import ai.metarank.model.FeatureSchema
import ai.metarank.util.Logging
import cats.data.{NonEmptyList, NonEmptyMap}
import cats.effect.IO
import io.circe.Decoder
import io.circe.yaml.parser.{parse => parseYaml}

case class Config(
    api: ApiConfig,
    state: StateStoreConfig,
    input: Option[InputConfig],
    features: NonEmptyList[FeatureSchema],
    models: Map[String, ModelConfig]
)

object Config extends Logging {

  implicit val configDecoder: Decoder[Config] = Decoder
    .instance(c =>
      for {
        apiOption   <- c.downField("api").as[Option[ApiConfig]]
        stateOption <- c.downField("state").as[Option[StateStoreConfig]]
        inputOption <- c.downField("input").as[Option[InputConfig]]
        features    <- c.downField("features").as[NonEmptyList[FeatureSchema]]
        models      <- c.downField("models").as[Map[String, ModelConfig]]
      } yield {
        val api   = get(apiOption, ApiConfig(Hostname("localhost"), Port(8080)), "api")
        val state = get(stateOption, MemoryStateConfig(), "state")
        Config(api, state, inputOption, features, models)
      }
    )
    .ensure(Validations.checkFeatureModelReferences)

  def get[T](opt: Option[T], default: T, name: String) = opt match {
    case Some(value) => value
    case None =>
      logger.info(s"$name conf block is not defined: using default $default")
      default
  }

  def load(contents: String): IO[Config] = {
    for {
      yaml    <- IO.fromEither(parseYaml(contents))
      decoded <- IO.fromEither(yaml.as[Config])
      _       <- logConfig(decoded)
    } yield {
      decoded
    }
  }

  def logConfig(conf: Config): IO[Unit] = IO {
    val stateType = conf.state match {
      case RedisStateConfig(host, port, db, cache, pipeline) => s"redis://$host:$port"
      case MemoryStateConfig()                               => "memory"
    }
    val features = conf.features.map(_.name.value).toList.mkString("[", ",", "]")
    val models   = conf.models.keys.mkString("[", ",", "]")
    logger.info(s"Loaded config file, state=$stateType, features=$features, models=$models")
  }

  object Validations {
    def checkFeatureModelReferences(config: Config): List[String] = {
      config.models.toList.flatMap {
        case (name, LambdaMARTConfig(_, features, _)) =>
          features.toList.flatMap(feature =>
            if (config.features.exists(_.name == feature)) None
            else Some(s"feature ${feature.value} referenced in model '$name', but missing in features section")
          )
        case _ => Nil
      }
    }
  }
}
