package ai.metarank.ml.onnx

import ai.metarank.flow.PrintProgress
import ai.metarank.ml.onnx.ScoreCache.ItemQueryKey
import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.CSVStream
import cats.effect.IO
import fs2.Stream

case class ScoreCache(cache: Map[ItemQueryKey, Float]) {
  def get(item: ItemId, query: String): Option[Float] = cache.get(ItemQueryKey(item, query))
}

object ScoreCache {
  case class ItemQueryKey(item: ItemId, query: String)

  def empty = ScoreCache(Map.empty)

  def fromCSV(path: String, delimiter: Char, skip: Int): IO[ScoreCache] =
    fromStream(CSVStream.fromFile(path, delimiter, skip))

  def fromStream(stream: Stream[IO, Array[String]]): IO[ScoreCache] =
    stream
      .evalMapChunk(row => parseRow(row))
      .through(PrintProgress.tap(None, "scores"))
      .compile
      .toList
      .map(rows => ScoreCache(rows.toMap))

  def parseRow(row: Array[String]): IO[(ItemQueryKey, Float)] = for {
    _     <- IO.whenA(row.length != 3)(IO.raiseError(new Exception(s"expected 3 columns, but got ${row.toList}")))
    score <- IO.fromOption(row(2).toFloatOption)(new Exception(s"cannot parse float value ${row(2)}"))
  } yield {
    (ItemQueryKey(ItemId(row(1)), row(0)), score)
  }
}
