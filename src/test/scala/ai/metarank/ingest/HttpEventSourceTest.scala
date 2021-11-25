package ai.metarank.ingest

import ai.metarank.config.IngestConfig.{APIIngestConfig, FileIngestConfig}
import ai.metarank.feature.FeatureMapping
import ai.metarank.mode.ingest.source.HttpEventSource
import ai.metarank.model.Event
import ai.metarank.util.{FlinkTest, RanklensEvents, TestConfig}
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
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.core.execution.JobClient
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.{EntityUtils, StringEntity}

import java.io.OutputStream
import scala.util.Random

class HttpEventSourceTest extends AnyFlatSpec with Matchers with FlinkTest with BeforeAndAfterAll {

  var client: JobClient = _
  lazy val outDir       = File.newTemporaryDirectory("events_").deleteOnExit()
  lazy val port         = Random.nextInt(60000) + 1024
  override def beforeAll() = {
    env.enableCheckpointing(1000)
    env.setRuntimeMode(RuntimeExecutionMode.AUTOMATIC)
    HttpEventSource(APIIngestConfig(port))
      .eventStream(env)
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
    val events = RanklensEvents(10000)

    val http = HttpClients.createDefault()
    for {
      event <- events.grouped(100)
    } {
      val request = new HttpPost(s"http://localhost:$port/")
      request.setEntity(new StringEntity(event.asJson.noSpaces, ContentType.APPLICATION_JSON))
      val response = http.execute(request)
      val reply    = EntityUtils.consume(response.getEntity)
      println(s"got ${response.getCode}")
    }
    client.triggerSavepoint("/tmp")
    outDir.listRecursively.filter(_.isRegularFile).map(_.size).sum should be > 10000L
  }
}
