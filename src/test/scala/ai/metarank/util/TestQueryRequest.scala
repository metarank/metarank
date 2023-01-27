package ai.metarank.util

import ai.metarank.ml.rank.QueryRequest
import ai.metarank.model.Event.RankItem
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Timestamp
import cats.data.NonEmptyList
import io.github.metarank.ltrlib.model.Query

import scala.util.Random

object TestQueryRequest {
  def apply(n: Int) = {
    val labels = new Array[Double](n)
    labels(Random.nextInt(n)) = 1.0
    val values = List
      .fill(n) {
        Random.nextDouble()
      }
      .toArray
    QueryRequest(
      items = NonEmptyList.fromListUnsafe((0 until n).map(i => RankItem(ItemId(s"p$i"))).toList),
      user = None,
      session = None,
      ts = Timestamp.now,
      query = Query(0, labels, values, 1, n)
    )
  }
}
