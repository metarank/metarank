package ai.metarank.format

import ai.metarank.config.SourceFormat.FormatReader
import ai.metarank.model.Event
import ai.metarank.source.format.JsonFormat
import ai.metarank.util.{TestInteractionEvent, TestItemEvent, TestRankingEvent, TestUserEvent}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class JsonFormatTest extends AnyFlatSpec with Matchers {
  val item = TestItemEvent("p1")
  val events: List[Event] = List(
    item,
    TestUserEvent("u1"),
    TestRankingEvent(List("p1", "p2")),
    TestInteractionEvent("p1", "r1")
  )

  it should "decode jsonl" in {
    val jsonl   = events.map(_.asJson.noSpaces).mkString("\n")
    val reader  = JsonFormat.reader(new ByteArrayInputStream(jsonl.getBytes(StandardCharsets.UTF_8)))
    val decoded = decode(reader)
    decoded shouldBe events
  }

  it should "decode json array" in {
    val jsona   = events.map(_.asJson.noSpaces).mkString("[", ",\n", "]")
    val reader  = JsonFormat.reader(new ByteArrayInputStream(jsona.getBytes(StandardCharsets.UTF_8)))
    val decoded = decode(reader)
    decoded shouldBe events
  }

  it should "read nothing on empty source" in {
    val reader  = JsonFormat.reader(new ByteArrayInputStream(Array.emptyByteArray))
    val decoded = reader.next()
    decoded shouldBe Right(None)
  }

  it should "decode jsonl with empty lines on end" in {
    val jsonl   = events.map(_.asJson.noSpaces).mkString("\n") + "\n\n\n"
    val reader  = JsonFormat.reader(new ByteArrayInputStream(jsonl.getBytes(StandardCharsets.UTF_8)))
    val decoded = decode(reader)
    decoded shouldBe events
  }

  it should "fail on non-json input" in {
    val reader  = JsonFormat.reader(new ByteArrayInputStream("YOLO".getBytes(StandardCharsets.UTF_8)))
    val decoded = reader.next()
    decoded shouldBe a[Left[_, _]]
  }

  it should "fail on garbage in json array" in {
    val jsona  = (events.map(_.asJson.noSpaces).take(1) ++ List("""{"yolo":1}""")).mkString("[", ",\n", "]")
    val reader = JsonFormat.reader(new ByteArrayInputStream(jsona.getBytes(StandardCharsets.UTF_8)))
    val e1     = reader.next()
    e1 shouldBe a[Left[_, _]]
  }

  def decode(reader: FormatReader): List[Event] = {
    Iterator
      .continually(reader.next())
      .collect {
        case Left(err)          => None
        case Right(Some(value)) => Some(value)
        case Right(None)        => None
      }
      .takeWhile(_.isDefined)
      .toList
      .flatten

  }
}
