package ai.metarank.source.format

import ai.metarank.config.SourceFormat
import ai.metarank.config.SourceFormat.FormatReader
import ai.metarank.model.Event
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser._
import io.circe.{Decoder, Json}
import io.circe.jawn.CirceSupportParser
import org.typelevel.jawn.AsyncParser
import org.typelevel.jawn.AsyncParser.UnwrapArray

import java.io.InputStream
import scala.collection.mutable

case object JsonArrayFormat extends SourceFormat {
  override def reader(stream: InputStream): SourceFormat.FormatReader = ???
  override def parse(bytes: Array[Byte]): Either[Throwable, Option[Event]] = JsonLineFormat.parse(bytes)

  case class JsonArrayReader(stream: InputStream, parser: AsyncParser[Json], queue: mutable.Queue[Event] , chunkSize: Int = 1024) extends FormatReader {
    import CirceSupportParser.facade

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
          events.flatMap(e => e.mEvent.eventCodec.decodeJson(e)) match {
            case Left(value) => Left(value)
            case Right(head :: tail) => queue.enqueueAll(tail)
          }
      }
      stream.
    }
  }


  case class Item(a: Int)
  val itemDec: Decoder[Item] = deriveDecoder
  def main(args: Array[String]): Unit = {
    val json            = """[{"a":1}, {"a":2}, {"a": 3}]""".grouped(5).toArray
    val parser          = AsyncParser[Json](UnwrapArray)
    implicit val facade = CirceSupportParser.facade
    val x1              = parser.absorb(json(0))
    val x2              = parser.absorb(json(1))
    val x3              = parser.absorb(json(2))
    val x4              = parser.absorb(json(3))
    val x5              = parser.absorb(json(4))
    val x6              = parser.absorb(json(5))
    val result          = (x2.toSeq.flatten ++ x4.toSeq.flatten ++ x6.toSeq.flatten).map(j => itemDec.decodeJson(j))
    val br              = 1

  }
}
