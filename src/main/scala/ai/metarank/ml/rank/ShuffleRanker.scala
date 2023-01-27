package ai.metarank.ml.rank

import ai.metarank.config.{ModelConfig, Selector}
import ai.metarank.config.Selector.AcceptSelector
import ai.metarank.ml.Model.{ItemScore, RankModel, Response}
import ai.metarank.ml.Predictor.RankPredictor
import ai.metarank.ml.{Model, Predictor}
import ai.metarank.model.ClickthroughValues
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveEncoder

import scala.util.Random

object ShuffleRanker {
  case class ShuffleConfig(maxPositionChange: Int, selector: Selector = AcceptSelector()) extends ModelConfig

  case class ShufflePredictor(name: String, config: ShuffleConfig) extends RankPredictor[ShuffleConfig, ShuffleModel] {
    override def load(bytes: Option[Array[Byte]]): Either[Throwable, ShuffleModel] =
      Right(ShuffleModel(name, config))
    override def fit(data: fs2.Stream[IO, ClickthroughValues]): IO[ShuffleModel] =
      IO.pure(ShuffleModel(name, config))
  }

  case class ShuffleModel(name: String, config: ShuffleConfig) extends RankModel {
    override def predict(request: QueryRequest): IO[Model.Response] = IO {
      Response(request.items.zipWithIndex.map {
        case (item, index) => {
          ItemScore(
            item = item.id,
            score = index.toDouble + Random.nextInt(2 * config.maxPositionChange) - config.maxPositionChange
          )

        }
      })
    }
    override def save(): Option[Array[Byte]] = None
  }

  implicit val shuffleDecoder: Decoder[ShuffleConfig] = Decoder.instance(c =>
    for {
      mpc      <- c.downField("maxPositionChange").as[Int]
      selector <- c.downField("selector").as[Option[Selector]].map(_.getOrElse(AcceptSelector()))
    } yield {
      ShuffleConfig(mpc, selector)
    }
  )
  implicit val shuffleEncoder: Encoder[ShuffleConfig] = deriveEncoder[ShuffleConfig]

}
