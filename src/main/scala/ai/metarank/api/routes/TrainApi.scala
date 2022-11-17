package ai.metarank.api.routes

import ai.metarank.FeatureMapping
import ai.metarank.api.JsonChunk
import ai.metarank.fstore.{ClickthroughStore, Persistence}
import ai.metarank.main.command.Train
import ai.metarank.main.command.train.SplitStrategy
import ai.metarank.rank.LambdaMARTModel
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.Chunk
import org.http4s.dsl.io._
import org.http4s.{Entity, HttpRoutes, Response, Status}
import scodec.bits.ByteVector

case class TrainApi(mapping: FeatureMapping, store: Persistence, cts: ClickthroughStore) extends Logging {
  def routes = HttpRoutes.of[IO] { case POST -> Root / "train" / modelName =>
    mapping.models.get(modelName) match {
      case Some(model @ LambdaMARTModel(conf, _, _, _)) =>
        Train
          .train(store, cts, model, modelName, conf.backend, SplitStrategy.default)
          .map(result => Response(entity = Entity.strict(JsonChunk(result))))
      case None =>
        error(Status.NotFound, s"Model $modelName is not defined in config")
      case Some(other) =>
        error(Status.BadRequest, s"Model $modelName ($other) cannot be trained")
    }
  }

  def error(status: Status, message: String) =
    IO.pure(Response(status = status, entity = Entity.strict(ByteVector(message.getBytes))))
}
