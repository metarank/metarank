package ai.metarank.main.api

import ai.metarank.api.routes.{FeedbackApi, TrainApi}
import ai.metarank.config.CoreConfig
import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.flow.ClickthroughJoinBuffer
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.{Timestamp, TrainResult}
import ai.metarank.util.RandomDataset
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import org.http4s.{Entity, Method, Request, Uri}
import fs2.Stream
import io.circe.parser._

class TrainApiTest extends AnyFlatSpec with Matchers {
  lazy val dataset = RandomDataset.generate(1000)
  lazy val state   = MemPersistence(dataset.mapping.schema)
  lazy val train   = TrainApi(dataset.mapping, state)
  lazy val buffer  = ClickthroughJoinBuffer(ClickthroughJoinConfig(), state, dataset.mapping)

  lazy val feedback = FeedbackApi(state, dataset.mapping, buffer)

  it should "ingest mock events" in {
    val json = dataset.events.map(_.asJson.noSpaces).mkString("[", ",", "]")
    val request = Request[IO](
      uri = Uri.unsafeFromString("http://localhost/feedback"),
      method = Method.POST,
      entity = Entity(Stream.emits(json.getBytes))
    )
    val response = feedback.routes(request).value.unsafeRunSync()
    response.map(_.status.code) shouldBe Some(200)
    buffer.flushQueue(Timestamp.max).unsafeRunSync()
  }
  it should "train the xgboost model" in {
    val request  = Request[IO](uri = Uri.unsafeFromString("http://localhost/train/xgboost"), method = Method.POST)
    val result   = train.routes(request).value.unsafeRunSync()
    val json     = result.map(_.entity.body.compile.toList.unsafeRunSync()).map(x => new String(x.toArray))
    val response = json.map(decode[TrainResult](_))
    response.map(_.map(_.features.size)) shouldBe Some(Right(2))
    response.map(_.map(_.iterations.size)) shouldBe Some(Right(10))
  }

  it should "respond with 404 on non-existent model" in {
    val request = Request[IO](uri = Uri.unsafeFromString("http://localhost/train/404"), method = Method.POST)
    val result  = train.routes(request).value.unsafeRunSync()
    result.map(_.status.code) shouldBe Some(404)
  }
}
