package ai.metarank.ingest

import ai.metarank.config.IngestConfig.FileIngestConfig
import ai.metarank.mode.ingest.source.FileEventSource
import ai.metarank.util.{EventGen, FlinkTest, TestSchemaConfig}
import better.files.File
import org.scalacheck.Gen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.{Checkers, ScalaCheckPropertyChecks}
import io.circe.syntax._
import org.apache.flink.api.scala._

class FileEventSourceTest extends AnyFlatSpec with Matchers with FlinkTest with ScalaCheckPropertyChecks with Checkers {

  override implicit val generatorDrivenConfig = PropertyCheckConfiguration(minSuccessful = 3)

  it should "read random stream of events" in {
    forAll(Gen.listOfN(1000, EventGen.eventGen(TestSchemaConfig()))) { events =>
      {
        val outDir  = File.newTemporaryDirectory("events_").deleteOnExit()
        val outFile = outDir.createChild("events.jsonl").deleteOnExit()
        val json    = events.map(_.asJson.noSpaces).mkString("\n")
        outFile.write(json)
        outFile.size should be > 1L
        val result = FileEventSource(FileIngestConfig(outDir.toString()))
          .eventStream(env)
          .executeAndCollect(2000)
        result should contain theSameElementsAs events
      }
    }
  }
}
