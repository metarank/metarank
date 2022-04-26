package ai.metarank.ingest

import ai.metarank.mode.inference.Inference.logo
import ai.metarank.mode.inference.api.{FeedbackApi, HealthApi, RankApi}
import ai.metarank.model.Event
import ai.metarank.source.FeedbackApiSource
import ai.metarank.util.{FlinkTest, TestItemEvent}
import cats.effect.{ExitCode, IO, Ref}
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import fs2.concurrent.SignallingRef
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.apache.flink.api.scala._
import scala.util.Random

class FeedbackApiSourceTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll with FlinkTest {
  val signal = SignallingRef[IO, Boolean](false).unsafeRunSync()
  val exit   = Ref[IO].of(ExitCode.Success).unsafeRunSync()
  val queue  = Queue.bounded[IO, Event](100).unsafeRunSync()
  val host   = "localhost"
  val port   = 1024 + Random.nextInt(50000)

  override def beforeAll() = {
    val httpApp = Router("/" -> FeedbackApi(queue).routes).orNotFound
    BlazeServerBuilder[IO]
      .bindHttp(port, host)
      .withHttpApp(httpApp)
      .serveWhile(signal, exit)
      .compile
      .drain
      .unsafeRunAndForget()
  }

  override def afterAll() = {
    signal.set(true).unsafeRunSync()

  }

  it should "receive event" in {
    val event = TestItemEvent("p1")
    queue.offer(event).unsafeRunSync()
    val received = env.addSource(FeedbackApiSource(host, port, 1)).executeAndCollect(1)
    received shouldBe List(event)
  }
}
