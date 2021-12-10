package ai.metarank.ingest

import ai.metarank.model.Event
import ai.metarank.source.LocalDirSource
import ai.metarank.source.LocalDirSource.LocalDirWriter
import ai.metarank.util.{FlinkTest, TestMetadataEvent}
import better.files.File
import org.apache.flink.api.common.RuntimeExecutionMode
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.apache.flink.api.scala._

class LocalDirSourceTest extends AnyFlatSpec with Matchers with FlinkTest {
  it should "accept events" in {
    env.setRuntimeMode(RuntimeExecutionMode.STREAMING)
    val path      = File.newTemporaryDirectory("events_").deleteOnExit()
    val writer    = new LocalDirWriter(path)
    val e1: Event = TestMetadataEvent("p1")
    val e2: Event = TestMetadataEvent("p2")
    writer.write(e1)
    writer.write(e2)
    val source = env.addSource(LocalDirSource(path.toString(), 1))
    val result = source.executeAndCollect(100)
    result shouldBe List(e1)
  }
}
