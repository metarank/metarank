package ai.metarank.main

import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.main.CliArgs.SortArgs
import ai.metarank.source.FileEventSource
import ai.metarank.util.SyntheticRanklensDataset
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{BufferedOutputStream, FileOutputStream}
import java.nio.file.Files
import scala.util.Random
import io.circe.syntax._

class SortTest extends AnyFlatSpec with Matchers {
  it should "sort single file" in {
    val events = Random.shuffle(SyntheticRanklensDataset(items = 100, users = 100))
    val file   = Files.createTempFile("events_in_", ".json")
    val out    = Files.createTempFile("events_out_", ".json")
    val stream = new BufferedOutputStream(new FileOutputStream(file.toFile), 10 * 1024)
    events.foreach(event => {
      stream.write(event.asJson.noSpaces.getBytes())
      stream.write('\n'.toInt)
    })
    stream.close()
    Sort.run(SortArgs(null, file, out)).unsafeRunSync()
    val sorted = FileEventSource(FileInputConfig(out.toString)).stream.compile.toList.unsafeRunSync()
    sorted shouldBe events.sortBy(_.timestamp.ts)
  }
}
