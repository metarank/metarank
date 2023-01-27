package ai.metarank.api.routes

import ai.metarank.FeatureMapping
import ai.metarank.api.JsonChunk
import ai.metarank.fstore.{ClickthroughStore, Persistence}
import ai.metarank.main.command.Train
import ai.metarank.main.command.train.SplitStrategy
import ai.metarank.ml.rank.LambdaMARTRanker.LambdaMARTPredictor
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.Chunk
import org.http4s.dsl.io._
import org.http4s.{Entity, HttpRoutes, Response, Status}
import scodec.bits.ByteVector

case class TrainApi(mapping: FeatureMapping, store: Persistence, cts: ClickthroughStore) extends Logging {
  def routes = HttpRoutes.of[IO] { case POST -> Root / "train" / modelName =>
    mapping.models.get(modelName) match {
      case Some(pred) =>
        Train
          .train(store, cts, pred)
          .map(result => Response(entity = Entity.strict(JsonChunk(result))))
      case None =>
        error(Status.NotFound, s"Model $modelName is not defined in config")
    }
  }

  def error(status: Status, message: String) =
    IO.pure(Response(status = status, entity = Entity.strict(ByteVector(message.getBytes))))
}
