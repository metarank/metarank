package ai.metarank.main.api

import ai.metarank.FeatureMapping
import ai.metarank.api.routes.FeedbackApi
import ai.metarank.config.BoosterConfig.XGBoostConfig
import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.feature.RandomFeature.RandomFeatureSchema
import ai.metarank.flow.TrainBuffer
import ai.metarank.fstore.memory.{MemTrainStore, MemPersistence}
import ai.metarank.ml.rank.LambdaMARTRanker.LambdaMARTConfig
import ai.metarank.model.Event
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.TrainValues.ClickthroughValues
import ai.metarank.util.{TestFeatureMapping, TestInteractionEvent, TestRankingEvent}
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Encoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax.*
import org.http4s.{Entity, Method, Request, Response, Uri}
import scodec.bits.ByteVector

import java.util.zip.GZIPInputStream

class FeedbackApiTest extends AnyFlatSpec with Matchers {
  lazy val mapping = TestFeatureMapping()
  lazy val store   = MemPersistence(mapping.schema)
  lazy val cs      = MemTrainStore()

  lazy val buffer  = TrainBuffer(ClickthroughJoinConfig(), store.values, cs, mapping)
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
      entity = Entity.stream(stream)
    )
    val response = service.routes(request).value.unsafeRunSync().get
    response.status.code shouldBe 200
  }

  it should "flush buffered clickthroughs on /flush" in {
    val models = Map(
      "lm" -> LambdaMARTConfig(
        backend = XGBoostConfig(),
        features = NonEmptyList.of(FeatureName("rand")),
        weights = Map("click" -> 1)
      )
    )
    val mapping2 =
      FeatureMapping.fromFeatureSchema(List(RandomFeatureSchema(FeatureName("rand"))), models).unsafeRunSync()
    val store2   = MemPersistence(mapping2.schema)
    val cs2      = MemTrainStore()
    val buffer2  = TrainBuffer(ClickthroughJoinConfig(), store2.values, cs2, mapping2)
    val service2 = FeedbackApi(store2, mapping2, buffer2)
    val ranking  = TestRankingEvent(List("p1", "p2"))
    send((ranking: Event).asJson.noSpaces, service2).status.code shouldBe 200
    send((TestInteractionEvent("p2", ranking.id.value): Event).asJson.noSpaces, service2).status.code shouldBe 200
    cs2.getall().compile.toList.unsafeRunSync() shouldBe empty
    flush(service2).status.code shouldBe 200
    flush(service2).status.code shouldBe 200
    val cts = cs2.getall().compile.toList.unsafeRunSync().collect {
      case ClickthroughValues(ct, _) if ct.interactions.nonEmpty => ct
    }
    cts.map(_.id) shouldBe List(ranking.id)
  }

  def send(payload: String, svc: FeedbackApi = service): Response[IO] = {
    val request = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString("http://localhost:8080/feedback"),
      entity = Entity.strict(ByteVector(payload.getBytes()))
    )

    svc.routes(request).value.unsafeRunSync().get
  }

  def flush(svc: FeedbackApi = service): Response[IO] = {
    val request = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString("http://localhost:8080/flush")
    )
    svc.routes(request).value.unsafeRunSync().get
  }
}
