package ai.metarank.ml.onnx

import ai.metarank.flow.PrintProgress
import ai.metarank.util.CSVStream
import cats.effect.IO

case class EmbeddingCache(cache: Map[String, Array[Float]]) {
  def get(key: String): Option[Array[Float]] = cache.get(key)
}

object EmbeddingCache {
  case class Embedding(key: String, emb: Array[Float])

  def empty(): EmbeddingCache = EmbeddingCache(Map.empty)
  def fromStream(stream: fs2.Stream[IO, Array[String]], dim: Int): IO[EmbeddingCache] =
    stream
      .parEvalMapUnordered(8)(line => IO.fromEither(parseEmbedding(line, dim)))
      .through(PrintProgress.tap(None, "embeddings"))
      .compile
      .toList
      .map(list => EmbeddingCache(list.map(e => e.key -> e.emb).toMap))

  def fromCSV(path: String, sep: Char, dim: Int): IO[EmbeddingCache] = {
    fromStream(CSVStream.fromFile(path, sep, 0), dim)
  }

  def parseEmbedding(line: Array[String], dim: Int): Either[Throwable, Embedding] = {
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
}
