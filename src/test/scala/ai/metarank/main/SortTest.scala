package ai.metarank.main

import ai.metarank.main.CliArgs.SortArgs
import ai.metarank.model.{Event, Timestamp}
import ai.metarank.util.TestItemEvent
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import io.circe.parser._
import scala.jdk.CollectionConverters._
import java.nio.file.Files
import scala.concurrent.duration._
import scala.util.Random

class SortTest extends AnyFlatSpec with Matchers {
  it should "sort events" in {
    val events: List[Event] = (0 until 1000)
      .map(i => TestItemEvent(i.toString).copy(timestamp = Timestamp.now.minus(Random.nextInt(100000).seconds)))
      .toList
    val file = Files.createTempFile("events-sort-in", ".json")
    val out  = Files.createTempFile("events-sort-out", ".json")
    Files.writeString(file, events.map(_.asJson.noSpaces).mkString("\n"))
    Sort.run(SortArgs(file, out)).unsafeRunSync()
    val decoded = Files.readAllLines(out).asScala.flatMap(line => decode[Event](line).toOption).toList
    val issorted = decoded.sliding(2).forall {
      case head :: last :: Nil => head.timestamp.isBeforeOrEquals(last.timestamp)
      case _                   => false
    }
    issorted shouldBe true
  }
}
