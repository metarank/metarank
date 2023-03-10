package ai.metarank.main.api

import ai.metarank.api.routes.FeedbackApi
import ai.metarank.config.CoreConfig
import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.flow.ClickthroughJoinBuffer
import ai.metarank.fstore.memory.{MemTrainStore, MemPersistence}
import ai.metarank.model.Event
import ai.metarank.util.{TestFeatureMapping, TestInteractionEvent, TestRankingEvent}
import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import fs2.Chunk
import io.circe.Encoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import org.http4s.{Entity, Method, Request, Response, Uri}
import scodec.bits.ByteVector

import java.util.zip.GZIPInputStream

class FeedbackApiTest extends AnyFlatSpec with Matchers {
  lazy val mapping = TestFeatureMapping()
  lazy val store   = MemPersistence(mapping.schema)
  lazy val cs      = MemTrainStore()

  lazy val buffer  = ClickthroughJoinBuffer(ClickthroughJoinConfig(), store.values, cs, mapping)
  lazy val service = FeedbackApi(store, mapping, buffer)

  it should "accept feedback events in json format" in {
    val event    = TestRankingEvent.event(List("p1")).asJson.noSpaces
    val response = send(event)
    response.status.code shouldBe 200
  }

  it should "accept feedback events in json-line format" in {
    val event    = TestRankingEvent.event(List("p1")).asJson.noSpaces
    val response = send(event + "\n" + event)
    response.status.code shouldBe 200
  }

  it should "accept feedback events in json-array format" in {
    val event =
      Encoder
        .encodeList[Event]
        .apply(List(TestRankingEvent.event(List("p1")), TestRankingEvent.event(List("p1"))))
        .noSpaces

    val response = send(event)
    response.status.code shouldBe 200
  }

  it should "accept interactions without ranking" in {
    val event    = TestInteractionEvent("p1", "neno").copy(ranking = None).asInstanceOf[Event].asJson.noSpaces
    val response = send(event)
    response.status.code shouldBe 200
  }

  it should "accept large batch of events" in {
    val events = new GZIPInputStream(this.getClass.getResourceAsStream("/ranklens/events/events.jsonl.gz"))
    val stream = fs2.io.readInputStream[IO](IO(events), 10 * 1024, closeAfterUse = false)
    val request = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString("http://localhost:8080/feedback"),
      entity = Entity(stream)
    )
    val response = service.routes(request).value.unsafeRunSync().get
    response.status.code shouldBe 200
  }

  def send(payload: String): Response[IO] = {
    val request = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString("http://localhost:8080/feedback"),
      entity = Entity.strict(ByteVector(payload.getBytes()))
    )

    service.routes(request).value.unsafeRunSync().get
  }
}
