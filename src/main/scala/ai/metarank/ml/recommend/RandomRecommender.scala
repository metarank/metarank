package ai.metarank.ml.recommend
import ai.metarank.config.Selector.AcceptSelector
import ai.metarank.config.{ModelConfig, Selector}
import ai.metarank.ml.Model.{ItemScore, RecommendModel, Response}
import ai.metarank.ml.Predictor.RecommendPredictor
import ai.metarank.ml.{Model, Predictor}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.TrainValues
import ai.metarank.model.TrainValues.ClickthroughValues
import ai.metarank.util.Logging
import cats.data.{NonEmptyList, NonEmptyVector}
import cats.effect.IO
import org.apache.commons.rng.sampling.PermutationSampler
import org.apache.commons.rng.simple.RandomSource

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}
import scala.util.Random

object RandomRecommender {
  case class RandomConfig(selector: Selector = AcceptSelector()) extends ModelConfig

  case class RandomModel(name: String, items: Array[ItemId]) extends RecommendModel {
    lazy val rnd = RandomSource.JDK.create()
    val VERSION  = 1

    override def predict(request: RecommendRequest): IO[Model.Response] = {
      if (request.count >= items.length) {
        IO(Random.shuffle(items.toList.map(i => ItemScore.apply(i, Random.nextDouble())))).flatMap {
          case head :: tail => IO.pure(Response(NonEmptyList.of(head, tail: _*)))
          case _            => IO.raiseError(new Exception("should never happen?"))
          // asked more than we have items
        }
      } else {
        for {
          // Fisher-Yates FTW!
          sampler <- IO(new PermutationSampler(rnd, items.length, request.count))
          samples <- IO(sampler.sample())
          response <- IO.fromOption(
            NonEmptyList.fromList(samples.map(index => ItemScore(items(index), Random.nextDouble())).toList)
          )(
            new Exception("empty samples")
          )
        } yield {
          Response(response)
        }
      }
    }

    override def save(): Option[Array[Byte]] = {
      val out    = new ByteArrayOutputStream()
      val stream = new DataOutputStream(out)
      stream.writeByte(VERSION)
      stream.writeInt(items.length)
      items.foreach(item => stream.writeUTF(item.value))
      stream.close()
      Some(out.toByteArray)
    }
  }

  case class RandomPredictor(name: String, config: RandomConfig)
      extends RecommendPredictor[RandomConfig, RandomModel]
      with Logging {
    override def load(bytes: Option[Array[Byte]]): Either[Throwable, RandomModel] = bytes match {
      case None => Left(new Exception("Cannot load model from store: not found. Did you train it before?"))
      case Some(bytes) =>
        val stream = new DataInputStream(new ByteArrayInputStream(bytes))
        val _      = stream.readByte()
        val size   = stream.readInt()
        val buffer = new Array[ItemId](size)
        var i      = 0
        while (i < size) {
          buffer(i) = ItemId(stream.readUTF())
          i += 1
        }
        logger.info(s"Loaded random recommender model: bytes=${bytes.length} items=$size")
        Right(RandomModel(name, buffer))

    }

    override def fit(data: fs2.Stream[IO, TrainValues]): IO[RandomModel] = {
      data
        .collect { case ct: ClickthroughValues => ct }
        .flatMap(ct => fs2.Stream(ct.ct.items: _*))
        .compile
        .fold(Set.empty[ItemId])((set, next) => set + next)
        .flatTap(buffer => info(s"trained random recommender model: items=${buffer.size}"))
        .map(set => RandomModel(name, set.toArray))

    }
  }

}
