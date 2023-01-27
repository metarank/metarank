package ai.metarank.ml.recommend.embedding

import com.google.common.collect.BiMap
import net.librec.math.structure.DenseMatrix
import scala.jdk.CollectionConverters._

case class EmbeddingMap(ids: Array[String], embeddings: Array[Array[Double]], rows: Int, cols: Int)

object EmbeddingMap {
  def apply(dic: BiMap[String, Integer], itemFactors: DenseMatrix) = {
    val ids = new Array[String](itemFactors.rowSize())
    dic.asScala.foreach { case (id, index) => ids(index) = id }
    new EmbeddingMap(
      ids = ids,
      embeddings = itemFactors.getValues,
      rows = itemFactors.rowSize(),
      cols = itemFactors.columnSize()
    )
  }
}
