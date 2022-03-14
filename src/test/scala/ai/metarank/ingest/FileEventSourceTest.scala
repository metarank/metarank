package ai.metarank.ingest

import ai.metarank.source.FileEventSource
import ai.metarank.util.{FlinkTest, RanklensEvents}
import better.files.File
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.scala._

class FileEventSourceTest extends AnyFlatSpec with Matchers with FlinkTest {

  it should "read random stream of events" in {
    val events = RanklensEvents(100)
    env.setRuntimeMode(RuntimeExecutionMode.BATCH)
    val outDir  = File.newTemporaryDirectory("events_").deleteOnExit()
    val outFile = outDir.createChild("events.jsonl").deleteOnExit()
    val json    = events.map(_.asJson.noSpaces).mkString("\n")
    outFile.write(json)
    outFile.size should be > 1L
    val result = FileEventSource("file:///" + outDir.toString())
      .eventStream(env)
      .executeAndCollect(2000)
    result should contain theSameElementsAs events
  }
}
