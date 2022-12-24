package ai.metarank.ml.recommend.embedding

import ai.metarank.ml.Model.ItemScore
import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.Logging
import com.github.jelmerk.knn.{DistanceFunctions, Index, Item, ProgressListener, SearchResult}
import com.github.jelmerk.knn.hnsw.HnswIndex

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util

case class HnswJavaIndex(index: HnswIndex[String, Array[Double], Embedding, java.lang.Double]) {
  def lookup(items: List[ItemId], n: Int): List[ItemScore] = items match {
    case head :: Nil =>
      makeResponse(index.findNeighbors(head.value, n))
    case head :: tail =>
      val embeddings = items.flatMap(item => index.get(item.value).toScala.map(_.vector)).toArray
      val center     = centroid(embeddings)
      makeResponse(index.findNearest(center, n))
    case Nil =>
      Nil
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

  def makeResponse(result: util.List[SearchResult[Embedding, java.lang.Double]]): List[ItemScore] =
    result.asScala.toList.map(sr => ItemScore(ItemId(sr.item().id), sr.distance()))

  def save(): Array[Byte] = {
    val stream = new ByteArrayOutputStream()
    index.save(stream)
    stream.toByteArray
  }
}

object HnswJavaIndex extends Logging {
  class LoggerListener extends ProgressListener {
    override def updateProgress(workDone: Int, max: Int): Unit =
      logger.info(s"indexed $workDone of $max items: ${math.round(100.0 * workDone / max)}%")
  }

  def create(source: EmbeddingMap, m: Int, ef: Int): HnswJavaIndex = {
    val builder = HnswIndex
      .newBuilder(source.cols, DistanceFunctions.DOUBLE_COSINE_DISTANCE, source.rows)
      .withM(m)
      .withEf(ef)
      .withEfConstruction(ef)
      .build[String, Embedding]()

    val embeddings = for {
      (embedding, index) <- source.embeddings.zipWithIndex
      id = source.ids(index)
    } yield {
      Embedding(id, embedding)
    }
    builder.addAll(util.Arrays.asList(embeddings: _*), 1, new LoggerListener(), 256)
    new HnswJavaIndex(builder)
  }

  def load(bytes: Array[Byte]): HnswJavaIndex = {
    val index = HnswIndex.load[String, Array[Double], Embedding, java.lang.Double](new ByteArrayInputStream(bytes))
    new HnswJavaIndex(index)
  }
}
