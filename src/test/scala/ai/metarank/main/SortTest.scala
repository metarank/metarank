package ai.metarank.main

import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.main.CliArgs.SortArgs
import ai.metarank.model.Event
import ai.metarank.source.FileEventSource
import ai.metarank.util.SyntheticRanklensDataset
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{BufferedOutputStream, FileOutputStream}
import java.nio.file.{Files, Path}
import scala.util.Random
import io.circe.syntax._

class SortTest extends AnyFlatSpec with Matchers {
  lazy val events = Random.shuffle(SyntheticRanklensDataset(items = 100, users = 100))

  it should "sort single file" in {
    val file = Files.createTempFile("events_in_", ".json")
    file.toFile.deleteOnExit()
    val out = Files.createTempFile("events_out_", ".json")
    out.toFile.deleteOnExit()
    writeBatch(events, file)

    Sort.run(SortArgs(file, out)).unsafeRunSync()
    val sorted = FileEventSource(FileInputConfig(out.toString)).stream.compile.toList.unsafeRunSync()
    sorted shouldBe events.sortBy(_.timestamp.ts)
  }

  it should "read directory of unsorted files" in {
    val dir = Files.createTempDirectory("events_in")
    val out = Files.createTempFile("events_out_", ".json")
    out.toFile.deleteOnExit()
    for {
      (batch, index) <- events.grouped(1000).zipWithIndex
    } {
      val file = Files.createTempFile(dir, s"events_in_${index}_", ".json")
      file.toFile.deleteOnExit()
      writeBatch(batch, file)
    }
    Sort.run(SortArgs(dir, out)).unsafeRunSync()
    val sorted = FileEventSource(FileInputConfig(out.toString)).stream.compile.toList.unsafeRunSync()
    sorted shouldBe events.sortBy(_.timestamp.ts)
  }

  def writeBatch(events: List[Event], file: Path) = {
    val stream = new BufferedOutputStream(new FileOutputStream(file.toFile), 10 * 1024)
    events.foreach(event => {
      stream.write(event.asJson.noSpaces.getBytes())
      stream.write('\n'.toInt)
    })
    stream.close()

  }
}
