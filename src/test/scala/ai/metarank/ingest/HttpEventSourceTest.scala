package ai.metarank.ingest

import ai.metarank.config.IngestConfig.{APIIngestConfig, FileIngestConfig}
import ai.metarank.ingest.source.{FileEventSource, HttpEventSource}
import ai.metarank.model.Event
import ai.metarank.util.{EventGen, FlinkTest, TestSchemaConfig}
import better.files.File
import org.apache.flink.api.common.serialization.Encoder
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.{Checkers, ScalaCheckPropertyChecks}
import org.apache.flink.api.scala._
import org.apache.flink.connector.file.sink.FileSink
import org.apache.flink.core.fs.Path
import io.circe.syntax._
import org.apache.flink.core.execution.JobClient
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.{EntityUtils, StringEntity}

import java.io.OutputStream

class HttpEventSourceTest
    extends AnyFlatSpec
    with Matchers
    with FlinkTest
    with ScalaCheckPropertyChecks
    with Checkers
    with BeforeAndAfterAll {
  override implicit val generatorDrivenConfig = PropertyCheckConfiguration(minSuccessful = 1)

  var client: JobClient = _
  lazy val outDir       = File.newTemporaryDirectory("events_").deleteOnExit()

  override def beforeAll() = {
    env.enableCheckpointing(1000)

    HttpEventSource(APIIngestConfig(8080))
      .source(env)
      .sinkTo(
        FileSink
          .forRowFormat[Event](
            new Path(outDir.toString),
            new Encoder[Event] {
              override def encode(element: Event, stream: OutputStream): Unit = {
                val json = element.asJson.noSpaces + "\n"
                stream.write(json.getBytes())
              }
            }
          )
          .build()
      )
    client = env.executeAsync("test")
  }

  override def afterAll() = {
    client.cancel().get()
  }

  it should "read random stream of events" in {
    forAll(Gen.listOfN(10000, EventGen.eventGen(TestSchemaConfig()))) { events =>
      {
        val http = HttpClients.createDefault()
        for {
          event <- events.grouped(100)
        } {
          val request = new HttpPost("http://localhost:8080/")
          request.setEntity(new StringEntity(event.asJson.noSpaces, ContentType.APPLICATION_JSON))
          val response = http.execute(request)
          val reply    = EntityUtils.consume(response.getEntity)
          println(s"got ${response.getCode}")
        }
        client.triggerSavepoint("/tmp")
        outDir.listRecursively.filter(_.isRegularFile).map(_.size).sum should be > 1000000L
      }
    }
  }
}
