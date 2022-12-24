package ai.metarank.ml.recommend

import ai.metarank.ml.Model
import ai.metarank.ml.Model.{RecommendModel, Response}
import ai.metarank.ml.Predictor.RecommendPredictor
import ai.metarank.ml.recommend.embedding.{EmbeddingMap, HnswJavaIndex}
import ai.metarank.ml.recommend.mf.MFRecImpl
import ai.metarank.ml.recommend.mf.MFRecImpl.MFModelConfig
import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.ClickthroughValues
import ai.metarank.util.Logging
import cats.data.NonEmptyList
import cats.effect.IO
import fs2.Chunk
import fs2.io.file.{Files, Path}

import java.nio.ByteBuffer

object MFRecommender {

  case class MFPredictor(name: String, config: MFModelConfig, mf: MFRecImpl)
      extends RecommendPredictor[MFModelConfig, MFModel]
      with Logging {
    override def fit(data: fs2.Stream[IO, ClickthroughValues]): IO[MFModel] = {
      Files[IO].tempFile.use(file =>
        for {
          _          <- info(s"writing training dataset to $file")
          _          <- writeUIRT(data, file)
          embeddings <- IO(mf.train(file))
          index      <- IO(HnswJavaIndex.create(embeddings, config.m, config.ef))
        } yield {
          MFModel(name, index)
        }
      )
    }

    override def load(bytes: Option[Array[Byte]]): Either[Throwable, MFModel] = bytes match {
      case Some(value) => Right(MFModel(name, HnswJavaIndex.load(value)))
      case None        => Left(new Exception(s"cannot load index $name: not found"))
    }

    def writeUIRT(source: fs2.Stream[IO, ClickthroughValues], dest: Path): IO[Unit] = {
      source
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

  case class MFModel(name: String, index: HnswJavaIndex) extends RecommendModel {
    override def predict(request: RecommendRequest): IO[Model.Response] = for {
      response <- IO(index.lookup(request.items, request.count))
      items    <- IO.fromOption(NonEmptyList.fromList(response))(new Exception("empty response from the recommender"))
    } yield {
      Response(items)
    }

    override def save(): Option[Array[Byte]] = Some(index.save())
  }

}
