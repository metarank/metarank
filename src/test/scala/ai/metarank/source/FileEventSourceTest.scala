package ai.metarank.source

import ai.metarank.config.EventSourceConfig.FileSourceConfig
import ai.metarank.config.MPath
import ai.metarank.util.{FlinkTest, RanklensEvents}
import better.files.File
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import org.apache.flink.api.common.RuntimeExecutionMode
import ai.metarank.mode.TypeInfos._

class FileEventSourceTest extends AnyFlatSpec with Matchers with FlinkTest {

  it should "read random stream of events" in {
    val events = RanklensEvents(100)
    env.setRuntimeMode(RuntimeExecutionMode.BATCH)
    val outDir  = File.newTemporaryDirectory("events_").deleteOnExit()
    val outFile = outDir.createChild("events.jsonl").deleteOnExit()
    val json    = events.map(_.asJson.noSpaces).mkString("\n")
    outFile.write(json)
    outFile.size should be > 1L
    val result = FileEventSource(FileSourceConfig(MPath("file:///" + outDir.toString())))
      .eventStream(env, bounded = false)
      .executeAndCollect(2000)
    result should contain theSameElementsAs events
  }
}
