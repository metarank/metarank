package ai.metarank.source

import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.util.RanklensEvents
import better.files.File
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import fs2.io.file.Path

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

  it should "sort files by name" in {
    val dir = File.newTemporaryDirectory("dir").deleteOnExit()
    dir.createChild("1").deleteOnExit()
    dir.createChild("4").deleteOnExit()
    dir.createChild("5").deleteOnExit()
    dir.createChild("3").deleteOnExit()
    dir.createChild("2").deleteOnExit()
    val source = FileEventSource(FileInputConfig(dir.toString()))
    val list   = source.listRecursive(Path(dir.toString())).compile.toList.unsafeRunSync().flatMap(_.names.lastOption)
    list.map(_.fileName.toString) shouldBe List("1", "2", "3", "4", "5")
  }
}
