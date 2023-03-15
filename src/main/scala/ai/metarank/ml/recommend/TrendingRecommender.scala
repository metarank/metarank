package ai.metarank.ml.recommend

import ai.metarank.config.{ModelConfig, Selector}
import ai.metarank.ml.Model
import ai.metarank.ml.Model.{ItemScore, RecommendModel, Response}
import ai.metarank.ml.Predictor.RecommendPredictor
import ai.metarank.model.{Timestamp, TrainValues}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.TrainValues.ClickthroughValues
import ai.metarank.util.Logging
import cats.data.NonEmptyList
import cats.effect.IO
import io.circe.generic.semiauto.{deriveCodec, deriveEncoder}
import io.circe.{Codec, Decoder, Encoder}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}
import scala.collection.mutable
import scala.concurrent.duration._

object TrendingRecommender {
  case class InteractionWeight(
      interaction: String,
      weight: Double = 1.0,
      decay: Double = 1.0,
      window: FiniteDuration = 30.days
  )
  case class TrendingConfig(
      weights: List[InteractionWeight],
      selector: Selector = Selector.AcceptSelector()
  ) extends ModelConfig

  val BITSTREAM_VERSION = 1

  case class ItemInteraction(item: ItemId, tpe: String, ts: Timestamp)

  case class TrendingPredictor(name: String, config: TrendingConfig)
      extends RecommendPredictor[TrendingConfig, TrendingModel]
      with Logging {
    override def fit(data: fs2.Stream[IO, TrainValues]): IO[TrendingModel] = for {
      ints <- data
        .collect { case ct: ClickthroughValues => ct }
        .flatMap(ct => fs2.Stream(ct.ct.interactions.map(ti => ItemInteraction(ti.item, ti.tpe, ct.ct.ts)): _*))
        .compile
        .toList
      now   <- IO.fromOption(ints.map(_.ts).maxByOption(_.ts))(new Exception("no interactions found"))
      _     <- info(s"loaded ${ints.size} interactions")
      items <- IO.fromOption(NonEmptyList.fromList(ints.map(_.item).distinct))(new Exception("no interactions found"))
    } yield {
      logger.info(s"found ${items.size} distinct items")
      val grouped = (for {
        weight <- config.weights
        timeThreshold = now.minus(weight.window)
        intSet        = ints.filter(i => i.ts.isAfter(timeThreshold) && (i.tpe == weight.interaction))
      } yield {
        val itemCounts = intSet.groupBy(_.item).map { case (item, ints) =>
          item -> ints
            .map(i => now.diff(i.ts).toDays.toInt)
            .foldLeft(new Array[Int](weight.window.toDays.toInt))((arr, i) => {
              arr(i) += 1
              arr
            })
        }
        logger.info(s"computed counts for type=${weight.interaction}, total items=${itemCounts.size}")
        weight.interaction -> itemCounts
      }).toMap
      val merged = (for {
        item <- items
      } yield {
        val sumParts = config.weights.map(weight => {
          grouped.get(weight.interaction).flatMap(_.get(item)) match {
            case Some(counts) =>
              var i = 0
              var s = 0.0
              while (i < counts.length) {
                s += counts(i) * math.pow(weight.decay, i)
                i += 1
              }
              s * weight.weight
            case None => 0.0
          }
        })
        TrendingItemScore(item, if (sumParts.isEmpty) 0.0 else sumParts.sum)
      })
      logger.info(s"per-type merging done, items=${merged.size}")
      TrendingModel(name, merged.sortBy(-_.score))

    }

    override def load(bytes: Option[Array[Byte]]): IO[TrendingModel] = IO.fromEither(loadSync(bytes))
    def loadSync(bytesOption: Option[Array[Byte]]): Either[Throwable, TrendingModel] = {
      bytesOption match {
        case None => Left(new Exception("cannot load trending model: not found"))
        case Some(bytes) =>
          val stream = new DataInputStream(new ByteArrayInputStream(bytes))
          stream.readInt() match {
            case BITSTREAM_VERSION =>
              val size = stream.readInt()
              val items = (0 until size).map(i => {
                val id    = ItemId(stream.readUTF())
                val score = stream.readDouble()
                TrendingItemScore(id, score)
              })
              items.toList match {
                case head :: tail => Right(TrendingModel(name, NonEmptyList.of(head, tail: _*)))
                case _            => Left(new Exception("no items found"))
              }
            case other => Left(new Exception(s"unsupported format $other"))
          }
      }

    }
  }

  case class TrendingItemScore(item: ItemId, score: Double)
  case class TrendingModel(name: String, items: NonEmptyList[TrendingItemScore]) extends RecommendModel {
    override def predict(request: RecommendRequest): IO[Model.Response] = {
      IO(items.take(request.count).map(i => ItemScore(i.item, i.score))).flatMap {
        case head :: tail => IO.pure(Response(NonEmptyList.of(head, tail: _*)))
        case _            => IO.raiseError(new Exception("count should be greater than 0"))
      }
    }

    override def save(): Option[Array[Byte]] = {
      val buf    = new ByteArrayOutputStream()
      val stream = new DataOutputStream(buf)
      stream.writeInt(BITSTREAM_VERSION)
      stream.writeInt(items.size)
      items.toList.foreach(item => {
        stream.writeUTF(item.item.value)
        stream.writeDouble(item.score)
      })
      Some(buf.toByteArray)
    }
  }

  import ai.metarank.util.DurationJson._
  implicit val intWeightDecoder: Decoder[InteractionWeight] = Decoder.instance(c =>
    for {
      tpe    <- c.downField("interaction").as[String]
      weight <- c.downField("weight").as[Option[Double]]
      decay  <- c.downField("decay").as[Option[Double]]
      window <- c.downField("window").as[Option[FiniteDuration]]
    } yield {
      val default = InteractionWeight(tpe)
      InteractionWeight(
        interaction = tpe,
        weight = weight.getOrElse(default.weight),
        decay = decay.getOrElse(default.decay),
        window = window.getOrElse(default.window)
      )
    }
  )

  implicit val intWeightEncoder: Encoder[InteractionWeight] = deriveEncoder
  implicit val intWeightCodec: Codec[InteractionWeight]     = Codec.from(intWeightDecoder, intWeightEncoder)

  implicit val trendingConfigDecoder: Decoder[TrendingConfig] = Decoder.instance(c =>
    for {
      weights  <- c.downField("weights").as[List[InteractionWeight]]
      selector <- c.downField("selector").as[Option[Selector]]
    } yield {
      TrendingConfig(weights, selector.getOrElse(Selector.AcceptSelector()))
    }
  )
  implicit val trendongConfigEncoder: Encoder[TrendingConfig] = deriveEncoder
  implicit val trendingConfigCodec: Codec[TrendingConfig]     = Codec.from(trendingConfigDecoder, trendongConfigEncoder)
}
