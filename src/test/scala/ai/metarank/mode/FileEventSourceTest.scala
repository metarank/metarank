package ai.metarank.mode

import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.config.MPath.LocalPath
import ai.metarank.model.Event
import ai.metarank.source.FileEventSource
import ai.metarank.util.TestInteractionEvent
import better.files.File
import cats.effect.unsafe.implicits.global
import fs2.io.file.Path
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._

import java.util.zip.GZIPOutputStream

class FileEventSourceTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {
  val dir     = File.newTemporaryDirectory().deleteOnExit()
  val child   = dir.createChild("child", asDirectory = true).deleteOnExit()
  val leaf    = child.createChild("events.json.gz").deleteOnExit()
  val exclude = child.createChild("conf.yml").deleteOnExit()
  val source  = FileEventSource(FileInputConfig(LocalPath(dir.toString())))
  val event   = TestInteractionEvent("p1", "p2").asInstanceOf[Event]

  override def beforeAll() = {
    val leafStream = new GZIPOutputStream(leaf.newFileOutputStream())
    leafStream.write((event.asJson.noSpaces ++ "\n" ++ event.asJson.noSpaces).getBytes())
    leafStream.close()
  }

  it should "list files" in {
    source.listRecursive(Path(dir.toString())).compile.toList.unsafeRunSync() shouldBe List(
      Path(leaf.toString()),
      Path(exclude.toString())
    )
  }

  it should "skip wrong formats" in {
    source.selectFile(Path(exclude.toString())) shouldBe false
  }

  it should "read events" in {
    val events = source.stream.compile.toList.unsafeRunSync()
    events shouldBe List(event, event)
  }

}
