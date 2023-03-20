package ai.metarank.ml.onnx.encoder

import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.io.file.Path

case class CsvEncoder(dic: Map[ItemId, Array[Float]], dim: Int) extends Encoder {
  override def encode(str: String): Array[Float] = ???
  override def encode(id: ItemId, str: String): Array[Float] = dic.get(id) match {
    case Some(value) => value
    case None        => new Array[Float](dim)
  }
}

object CsvEncoder extends Logging {
  def create(lines: fs2.Stream[IO, String]) = for {
    dic <- lines
      .filter(_.nonEmpty)
      .evalMap(line => IO.fromEither(parseLine(line)))
      .compile
      .toList
    _ <- info(s"loaded ${dic.size} embeddings")
    size <- IO(dic.map(_._2.length).distinct).flatMap {
      case Nil        => IO.raiseError(new Exception("no embeddings found"))
      case one :: Nil => IO.pure(one)
      case other      => IO.raiseError(new Exception(s"all embedding sizes should be the same, but got $other"))
    }
  } yield {
    CsvEncoder(dic.toMap, size)
  }

  def create(path: String): IO[CsvEncoder] = {
    create(fs2.io.file.Files[IO].readUtf8Lines(Path(path)))
  }

  def parseLine(line: String): Either[Throwable, (ItemId, Array[Float])] = {
    val tokens = line.split(',')
    if (tokens.length > 1) {
      val key    = ItemId(tokens(0))
      val values = new Array[Float](tokens.length - 1)
      var i      = 1
      var failed = false
      while ((i < tokens.length) && !failed) {
        tokens(i).toFloatOption match {
          case Some(float) => values(i - 1) = float
          case None        => failed = true
        }
        i += 1
      }
      if (failed) Left(new Exception(s"cannot parse line $line")) else Right((key, values))
    } else {
      Left(new Exception("cannot parse embedding"))
    }
  }

}
