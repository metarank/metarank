package ai.metarank.ml.recommend

import ai.metarank.ml.Model
import ai.metarank.ml.Model.{RecommendModel, Response}
import ai.metarank.ml.Predictor.RecommendPredictor
import ai.metarank.ml.recommend.embedding.KnnIndex.KnnIndexReader
import ai.metarank.ml.recommend.embedding.{EmbeddingMap, HnswJavaIndex, KnnIndex}
import ai.metarank.ml.recommend.mf.MFRecImpl
import ai.metarank.ml.recommend.mf.MFRecImpl.MFModelConfig
import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.TrainValues
import ai.metarank.model.TrainValues.ClickthroughValues
import ai.metarank.util.Logging
import cats.data.NonEmptyList
import cats.effect.IO
import fs2.Chunk
import fs2.io.file.{Files, Path}

import java.nio.ByteBuffer

object MFRecommender {

  case class MFPredictor(name: String, config: MFModelConfig, mf: MFRecImpl)
      extends RecommendPredictor[MFModelConfig, EmbeddingSimilarityModel]
      with Logging {
    override def fit(data: fs2.Stream[IO, TrainValues]): IO[EmbeddingSimilarityModel] = {
      Files[IO].tempFile.use(file =>
        for {
          _          <- info(s"writing training dataset to $file")
          _          <- writeUIRT(data, file)
          embeddings <- IO(mf.train(file))
          index      <- KnnIndex.write(embeddings, config.store)
        } yield {
          EmbeddingSimilarityModel(name, index)
        }
      )
    }

    override def load(bytes: Option[Array[Byte]]): IO[EmbeddingSimilarityModel] = bytes match {
      case Some(value) => KnnIndex.load(value, config.store).map(index => EmbeddingSimilarityModel(name, index))
      case None        => IO.raiseError(new Exception(s"cannot load index $name: not found"))
    }

    def writeUIRT(source: fs2.Stream[IO, TrainValues], dest: Path): IO[Unit] = {
      source
        .collect { case ct: ClickthroughValues => ct }
        .filter(_.ct.interactions.nonEmpty)
        .flatMap(ctv => fs2.Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(uirt(ctv).mkString("").getBytes()))))
        .through(Files[IO].writeAll(dest))
        .compile
        .drain
    }

    def uirt(ctv: ClickthroughValues): List[String] = for {
      int  <- ctv.ct.interactions if shouldAccept(int)
      user <- ctv.ct.user.map(_.value)
    } yield {
      s"$user,${int.item.value},1,${ctv.ct.ts.ts}\n"
    }

    def shouldAccept(int: TypedInteraction): Boolean = {
      config.interactions.isEmpty || config.interactions.contains(int.tpe)
    }
  }

  case class EmbeddingSimilarityModel(name: String, index: KnnIndexReader) extends RecommendModel {
    override def predict(request: RecommendRequest): IO[Model.Response] = for {
      _ <- request.items match {
        case Nil => IO.raiseError(new Exception("similar items recommender requires request.items to be non-empty"))
        case _   => IO.unit
      }
      response <- index.lookup(request.items, request.count + request.items.size)
      filtered <- IO(response.filterNot(is => request.items.contains(is.item)).take(request.count))
      items    <- IO.fromOption(NonEmptyList.fromList(filtered))(new Exception("empty response from the recommender"))
    } yield {
      Response(items)
    }

    override def save(): Option[Array[Byte]] = Some(index.save())
  }

}
