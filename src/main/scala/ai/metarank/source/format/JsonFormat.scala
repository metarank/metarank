package ai.metarank.source.format

import ai.metarank.config.SourceFormat
import ai.metarank.config.SourceFormat.FormatReader
import ai.metarank.model.Event
import ai.metarank.util.Logging
import io.circe.Json
import io.circe.jawn.CirceSupportParser
import io.circe.parser._
import org.typelevel.jawn.AsyncParser
import org.typelevel.jawn.AsyncParser.UnwrapArray

import java.io.{InputStream, PushbackInputStream}
import java.nio.charset.StandardCharsets
import scala.annotation.tailrec
import scala.collection.mutable

case object JsonFormat extends SourceFormat with Logging {
  val BRACKET: Int = '['.toInt

  override def reader(stream: InputStream): SourceFormat.FormatReader = {
    val pushBack = new PushbackInputStream(stream, 1)
    val first    = pushBack.read()
    if (first == -1) {
      // empty stream
      logger.info("json stream is empty")
      EmptyReader
    } else if (first == BRACKET) {
      // a json array
      logger.info("json stream looks like a JSON array, using JsonArrayReader")
      pushBack.unread(first)
      JsonArrayReader(pushBack)
    } else {
      logger.info("json stream seem to be jsonl-encoded, using JsonLineReader")
      pushBack.unread(first)
      JsonLineReader(pushBack)
    }
  }

  override def parse(bytes: Array[Byte]): Either[Throwable, Option[Event]] =
    decode[Event](new String(bytes, StandardCharsets.UTF_8)).map(Option.apply)

  case class JsonLineReader(stream: InputStream) extends FormatReader {
    override def next(): Either[Throwable, Option[Event]] = {
      readLine(stream) match {
        case None       => Right(None)
        case Some(line) => JsonFormat.parse(line)
      }
    }
  }

  case class JsonArrayReader(stream: InputStream, chunkSize: Int = 1024) extends FormatReader {
    import CirceSupportParser.facade
    val parser: AsyncParser[Json]   = AsyncParser[Json](UnwrapArray)
    val queue: mutable.Queue[Event] = mutable.Queue()

    override def next(): Either[Throwable, Option[Event]] = {
      queue.dequeueFirst(_ => true) match {
        case Some(buffered) => Right(Some(buffered))
        case None =>
          val chunk = stream.readNBytes(chunkSize)
          val events = if (chunk.length < chunkSize) {
            parser.finalAbsorb(chunk)
          } else {
            parser.absorb(chunk)
          }
          events match {
            case Left(err) => Left(err)
            case Right(jsons) =>
              decodeRecursive(jsons.toList) match {
                case Left(err) => Left(err)
                case Right(head :: tail) =>
                  queue.enqueueAll(tail)
                  Right(Some(head))
                case Right(Nil) =>
                  Right(None)
              }
          }
      }
    }

    @tailrec
    private def decodeRecursive(tail: List[Json], acc: List[Event] = Nil): Either[Throwable, List[Event]] = {
      tail match {
        case Nil => Right(acc)
        case head :: tail =>
          Event.eventCodec.decodeJson(head) match {
            case Left(err)   => Left(err)
            case Right(next) => decodeRecursive(tail, acc :+ next)
          }
      }
    }
  }

  case object EmptyReader extends FormatReader {
    override def next(): Either[Throwable, Option[Event]] = Right(None)
  }

}
