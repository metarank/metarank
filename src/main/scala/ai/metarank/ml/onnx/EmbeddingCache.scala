package ai.metarank.ml.onnx

import ai.metarank.model.Identifier.ItemId
import ai.metarank.flow.PrintProgress
import ai.metarank.util.CSVStream
import cats.effect.IO

case class EmbeddingCache[K](cache: Map[K, Array[Float]]) {
  def get(key: K): Option[Array[Float]] = cache.get(key)
}

object EmbeddingCache {
  case class ItemQueryKey(item: ItemId, query: String)
  case class Embedding[K](key: K, emb: Array[Float])

  def empty[K](): EmbeddingCache[K] = EmbeddingCache(Map.empty)
  def fromStreamString(stream: fs2.Stream[IO, Array[String]], dim: Int): IO[EmbeddingCache[String]] =
    stream
      .evalMapChunk(line => IO.fromEither(parseEmbeddingString(line, dim)))
      .through(PrintProgress.tap(None, "embeddings"))
      .compile
      .toList
      .map(list => EmbeddingCache(list.map(e => e.key -> e.emb).toMap))

  def fromCSVString(path: String, sep: Char, dim: Int): IO[EmbeddingCache[String]] = {
    fromStreamString(CSVStream.fromFile(path, sep, 0), dim)
  }

  def fromStreamItemQuery(stream: fs2.Stream[IO, Array[String]], dim: Int): IO[EmbeddingCache[ItemQueryKey]] =
    stream
      .evalMapChunk(line => IO.fromEither(parseEmbeddingItemQuery(line, dim)))
      .compile
      .toList
      .map(list => EmbeddingCache(list.map(e => e.key -> e.emb).toMap))

  def fromCSVItemQuery(path: String, sep: Char, dim: Int): IO[EmbeddingCache[ItemQueryKey]] = {
    fromStreamItemQuery(CSVStream.fromFile(path, sep, 0), dim)
  }

  def parseEmbeddingString(line: Array[String], dim: Int): Either[Throwable, Embedding[String]] = {
    if (line.length != dim + 1) {
      Left(new Exception(s"dim mismatch for line ${line.toList}: expected $dim, got line with ${line.length} cols"))
    } else {
      val key    = line(0)
      val buffer = new Array[Float](dim)
      var error  = false
      var i      = 1
      while (!error && (i < line.length)) {
        line(i).toFloatOption match {
          case Some(value) => buffer(i - 1) = value
          case None        => error = true
        }
        i += 1
      }
      if (error) {
        Left(new Exception(s"cannot parse line ${line.toList}"))
      } else {
        Right(Embedding(key, buffer))
      }
    }
  }

  def parseEmbeddingItemQuery(line: Array[String], dim: Int): Either[Throwable, Embedding[ItemQueryKey]] = {
    if (line.length != dim + 2) {
      Left(new Exception(s"dim mismatch for line ${line.toList}"))
    } else {
      val item   = ItemId(line(0))
      val query  = line(1)
      val buffer = new Array[Float](dim)
      var error  = false
      var i      = 2
      while (!error && (i < line.length)) {
        line(i).toFloatOption match {
          case Some(value) => buffer(i - 2) = value
          case None        => error = true
        }
        i += 1
      }
      if (error) {
        Left(new Exception(s"cannot parse line ${line.toList}"))
      } else {
        Right(Embedding(ItemQueryKey(item, query), buffer))
      }
    }
  }
}
