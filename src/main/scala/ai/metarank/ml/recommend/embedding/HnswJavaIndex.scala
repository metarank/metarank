package ai.metarank.ml.recommend.embedding

import ai.metarank.ml.Model.ItemScore
import ai.metarank.ml.recommend.embedding.KnnIndex.{KnnIndexReader, KnnIndexWriter}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.Logging
import cats.effect.IO
import com.github.jelmerk.hnswlib.core.{DistanceFunctions, Index, Item, ProgressListener, SearchResult}
import com.github.jelmerk.hnswlib.core.hnsw.HnswIndex

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util
import fs2.{Chunk, Stream}

object HnswJavaIndex extends Logging {
  class LoggerListener extends ProgressListener {
    override def updateProgress(workDone: Int, max: Int): Unit =
      logger.info(s"indexed $workDone of $max items: ${math.round(100.0 * workDone / max)}%")
  }

  case class HnswIndexReader(index: HnswIndex[String, Array[Double], Embedding, java.lang.Double])
      extends KnnIndexReader {
    override def lookup(items: List[ItemId], n: Int): IO[List[ItemScore]] = IO {
      items match {
        case Nil => Nil
        case head :: Nil =>
          index.get(head.value).toScala match {
            case Some(value) => lookupOne(value.vector, n)
            case None        => Nil
          }
        case _ =>
          val embeddings = items.flatMap(item => index.get(item.value).toScala.map(_.vector)).toArray
          val center     = centroid(embeddings)
          lookupOne(center, n)
      }
    }

    def centroid(items: Array[Array[Double]]): Array[Double] = {
      val result = new Array[Double](index.getDimensions)
      var i      = 0
      while (i < result.length) {
        var sum       = 0.0
        var itemIndex = 0
        while (itemIndex < items.length) {
          sum += items(itemIndex)(i)
          itemIndex += 1
        }
        result(i) = sum / items.length
        i += 1
      }
      result
    }

    def lookupOne(vector: Array[Double], n: Int): List[ItemScore] = {
      val result = index.findNearest(vector, n)
      result.asScala.toList.map(sr => ItemScore(ItemId(sr.item().id), sr.distance()))
    }

    override def save(): Array[Byte] = {
      val stream = new ByteArrayOutputStream()
      index.save(stream)
      stream.toByteArray
    }
  }

  object HnswIndexWriter extends KnnIndexWriter[HnswIndexReader, HnswOptions] {
    override def write(source: EmbeddingMap, options: HnswOptions): IO[HnswIndexReader] = for {
      index <- IO(
        HnswIndex
          .newBuilder(source.cols, DistanceFunctions.DOUBLE_COSINE_DISTANCE, source.rows)
          .withM(options.m)
          .withEf(options.ef)
          .withEfConstruction(options.ef)
          .build[String, Embedding]()
      )
      emb <- Stream
        .chunk[IO, Array[Double]](Chunk.array(source.embeddings))
        .zip(Stream.chunk[IO, String](Chunk.array(source.ids)))
        .map { case (embedding, id) => Embedding(id, embedding) }
        .compile
        .toList
      _ <- IO(index.addAll(util.Arrays.asList(emb: _*), 1, new LoggerListener(), 256))
    } yield {
      HnswIndexReader(index)
    }

    override def load(bytes: Array[Byte], options: HnswOptions): IO[HnswIndexReader] = IO {
      val index = HnswIndex.load[String, Array[Double], Embedding, java.lang.Double](new ByteArrayInputStream(bytes))
      HnswIndexReader(index)
    }
  }

  case class HnswOptions(m: Int, ef: Int)
}
