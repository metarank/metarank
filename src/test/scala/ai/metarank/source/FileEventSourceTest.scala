package ai.metarank.source

import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.config.InputConfig.FileInputConfig.SortingType.{SortByName, SortByTime}
import ai.metarank.config.InputConfig.SourceOffset.{Earliest, ExactTimestamp}
import ai.metarank.model.{Event, Timestamp}
import ai.metarank.util.{RanklensEvents, TestItemEvent}
import better.files.File
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import fs2.io.file.Path

import scala.concurrent.duration._

class FileEventSourceTest extends AnyFlatSpec with Matchers {
  lazy val events = RanklensEvents(100)

  it should "read random stream of events from multiple files" in {
    val outDir  = File.newTemporaryDirectory("events_").deleteOnExit()
    val outFile = outDir.createChild("events.jsonl").deleteOnExit()
    val json    = events.map(_.asJson.noSpaces).mkString("\n")
    outFile.write(json)
    outFile.size should be > 1L
    val result  = FileEventSource(FileInputConfig(outDir.toString()))
    val decoded = result.stream.compile.toList.unsafeRunSync()
    decoded shouldBe events
  }

  it should "read events from single file" in {
    val outFile = File.newTemporaryFile("events", suffix = ".json").deleteOnExit()
    val json    = events.map(_.asJson.noSpaces).mkString("\n")
    outFile.write(json)
    outFile.size should be > 1L
    val result  = FileEventSource(FileInputConfig(outFile.toString()))
    val decoded = result.stream.compile.toList.unsafeRunSync()
    decoded shouldBe events
  }

  it should "sort files" in {
    val dir = File.newTemporaryDirectory("dir").deleteOnExit()
    dir.createChild("1").deleteOnExit()
    Thread.sleep(10)
    dir.createChild("4").deleteOnExit()
    Thread.sleep(10)
    dir.createChild("5").deleteOnExit()
    Thread.sleep(10)
    dir.createChild("3").deleteOnExit()
    Thread.sleep(10)
    dir.createChild("2").deleteOnExit()

    val sourceName = FileEventSource(FileInputConfig(dir.toString(), sort = SortByName))
    val listName =
      sourceName.listRecursive(Path(dir.toString())).compile.toList.unsafeRunSync().flatMap(_.names.lastOption)
    listName.map(_.fileName.toString) shouldBe List("1", "2", "3", "4", "5")

    val sourceTime = FileEventSource(FileInputConfig(dir.toString(), sort = SortByTime))
    val listTime =
      sourceTime.listRecursive(Path(dir.toString())).compile.toList.unsafeRunSync().flatMap(_.names.lastOption)
    listTime.map(_.fileName.toString) shouldBe List("1", "4", "5", "3", "2")
  }

  it should "obey offset" in {
    val now = Timestamp.now
    val events = List[Event](
      TestItemEvent("p1").copy(timestamp = now.minus(1.minute)),
      TestItemEvent("p1").copy(timestamp = now),
      TestItemEvent("p1").copy(timestamp = now.plus(1.minute))
    )
    val outFile = File.newTemporaryFile("events", suffix = ".json").deleteOnExit()
    val json    = events.map(_.asJson.noSpaces).mkString("\n")
    outFile.write(json)

    val earliest =
      FileEventSource(FileInputConfig(outFile.toString(), offset = Earliest)).stream.compile.toList.unsafeRunSync()
    earliest shouldBe events

    val exact = FileEventSource(
      FileInputConfig(outFile.toString(), offset = ExactTimestamp(now.ts))
    ).stream.compile.toList.unsafeRunSync()

    exact shouldBe events.drop(1)
  }
}
