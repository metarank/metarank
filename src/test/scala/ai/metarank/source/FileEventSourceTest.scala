package ai.metarank.source

import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.util.RanklensEvents
import better.files.File
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._

class FileEventSourceTest extends AnyFlatSpec with Matchers {

  it should "read random stream of events" in {
    val events  = RanklensEvents(100)
    val outDir  = File.newTemporaryDirectory("events_").deleteOnExit()
    val outFile = outDir.createChild("events.jsonl").deleteOnExit()
    val json    = events.map(_.asJson.noSpaces).mkString("\n")
    outFile.write(json)
    outFile.size should be > 1L
    val result  = FileEventSource(FileInputConfig(outDir.toString()))
    val decoded = result.stream.compile.toList.unsafeRunSync()
    decoded shouldBe events
  }
}
