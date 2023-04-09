package ai.metarank.util

import cats.effect.{IO, Resource}
import com.opencsv.{CSVParserBuilder, CSVReader, CSVReaderBuilder}
import fs2.Stream

import scala.jdk.CollectionConverters._
import java.io.{File, FileInputStream, InputStream, InputStreamReader}

object CSVStream extends Logging {
  val CHUNK_SIZE = 1024
  def fromStream(stream: InputStream, delimiter: Char, skip: Int): Stream[IO, Array[String]] = for {
    parser <- Stream.eval(IO(new CSVParserBuilder().withSeparator(delimiter).build()))
    reader <- Stream.eval(
      IO(new CSVReaderBuilder(new InputStreamReader(stream)).withCSVParser(parser).withSkipLines(skip).build())
    )
    rows <- Stream.fromBlockingIterator[IO](reader.iterator().asScala, CHUNK_SIZE)
  } yield {
    rows
  }

  def fromFile(path: String, delimiter: Char, skip: Int): Stream[IO, Array[String]] = for {
    stream <- Stream.resource(Resource.make(IO(new FileInputStream(new File(path))))(x => IO(x.close())))
    rows   <- fromStream(stream, delimiter, skip)
  } yield {
    rows
  }
}
