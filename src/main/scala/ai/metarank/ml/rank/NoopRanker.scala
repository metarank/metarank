package ai.metarank.ml.rank

import ai.metarank.config.Selector.AcceptSelector
import ai.metarank.config.{ModelConfig, Selector}
import ai.metarank.ml.Model.{ItemScore, RankModel, Response}
import ai.metarank.ml.Predictor.RankPredictor
import ai.metarank.ml.{Model, Predictor}
import ai.metarank.model.TrainValues
import cats.effect.IO
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

object NoopRanker {
  case class NoopConfig(selector: Selector = AcceptSelector()) extends ModelConfig
  case class NoopPredictor(name: String, config: NoopConfig) extends RankPredictor[NoopConfig, NoopModel] {
    override def load(bytes: Option[Array[Byte]]): Either[Throwable, NoopModel] = Right(NoopModel(name, config))

    override def fit(data: fs2.Stream[IO, TrainValues]): IO[NoopModel] =
      IO.pure(NoopModel(name, config))
  }

  case class NoopModel(name: String, config: NoopConfig) extends RankModel {
    override def predict(request: QueryRequest): IO[Model.Response] = IO(
      Response(request.items.map(i => ItemScore(i.id, 0.0)))
    )
    override def save(): Option[Array[Byte]] = None
  }

  implicit val noopDecoder: Decoder[NoopConfig] =
    Decoder.instance(c =>
      c.downField("selector").as[Option[Selector]].map(_.getOrElse(AcceptSelector())).map(NoopConfig)
    )
  implicit val noopEncoder: Encoder[NoopConfig] = deriveEncoder[NoopConfig]

}
