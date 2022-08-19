package ai.metarank.main.api

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.fstore.Persistence
import ai.metarank.main.command.Train
import ai.metarank.rank.LambdaMARTModel
import ai.metarank.source.ModelCache
import ai.metarank.util.Logging
import cats.effect.IO
import org.http4s.dsl.io._
import org.http4s.{Entity, HttpRoutes, Response, Status}
import cats.implicits._
import fs2.Chunk
import io.circe.syntax._

case class TrainApi(mapping: FeatureMapping, store: Persistence, models: ModelCache) extends Logging {
  def routes = HttpRoutes.of[IO] { case POST -> Root / "train" / modelName =>
    mapping.models.get(modelName) match {
      case Some(model @ LambdaMARTModel(conf, _, _, _)) =>
        Train
          .train(store, model, modelName, conf.backend)
          .flatTap(_ => models.invalidate(modelName))
          .map(result => Response(entity = Entity.strict(Chunk.array(result.asJson.spaces2SortKeys.getBytes))))
      case None =>
        error(Status.NotFound, s"Model $modelName is not defined in config")
      case Some(other) =>
        error(Status.BadRequest, s"Model $modelName ($other) cannot be trained")
    }
  }

  def error(status: Status, message: String) =
    IO.pure(Response(status = status, entity = Entity.strict(Chunk.array(message.getBytes))))
}
