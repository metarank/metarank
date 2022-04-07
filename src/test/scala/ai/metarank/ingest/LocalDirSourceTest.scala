package ai.metarank.ingest

import ai.metarank.model.Event
import ai.metarank.source.LocalDirSource
import ai.metarank.source.LocalDirSource.LocalDirWriter
import ai.metarank.util.{FlinkTest, TestItemEvent}
import better.files.File
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import org.apache.flink.api.common.RuntimeExecutionMode
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.apache.flink.api.scala._

import scala.util.Random

class LocalDirSourceTest extends AnyFlatSpec with Matchers with FlinkTest {
  it should "accept events" in {
    env.setRuntimeMode(RuntimeExecutionMode.STREAMING)
    val path      = File.newTemporaryDirectory("events_").deleteOnExit()
    val writer    = new LocalDirWriter(path, Ref.unsafe(0))
    val e1: Event = TestItemEvent("p1")
    val e2: Event = TestItemEvent("p2")
    writer.write(e1).unsafeRunSync()
    writer.write(e2).unsafeRunSync()
    val source = env.addSource(LocalDirSource(path.toString(), 1))
    val result = source.executeAndCollect(1000)
    result shouldBe List(e1)
  }

  it should "not race" in {
    val events = List.fill(1000)(TestItemEvent(Random.nextInt().toString))
    val path   = File.newTemporaryDirectory("events_").deleteOnExit()
    val writer = new LocalDirWriter(path, Ref.unsafe(0))
    events.par.foreach(e => writer.write(e).unsafeRunSync())
    val source = env.addSource(LocalDirSource(path.toString(), 1000))
    val result = source.executeAndCollect(1000)
    result.size shouldBe events.size
  }
}
