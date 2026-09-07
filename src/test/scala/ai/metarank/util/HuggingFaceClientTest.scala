package ai.metarank.util

import ai.metarank.ml.onnx.{HuggingFaceClient, ModelHandle}
import ai.metarank.ml.onnx.HuggingFaceClient.ModelResponse.Sibling
import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import org.http4s.{HttpApp, Response, Status, Uri}
import org.http4s.client.Client
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

class HuggingFaceClientTest extends AnyFlatSpec with Matchers {
  given LoggerFactory[IO] = Slf4jFactory.create[IO]

  // in-memory server: fails with `failStatus` for the first `failures` calls, then serves "ok"
  def flakyClient(failures: Int, failStatus: Status): IO[(HuggingFaceClient, Ref[IO, Int])] = for {
    counter <- Ref.of[IO, Int](0)
    app = HttpApp[IO] { _ =>
      counter.updateAndGet(_ + 1).map { n =>
        if (n <= failures) Response[IO](failStatus) else Response[IO](Status.Ok).withEntity("ok")
      }
    }
    raw    = Client.fromHttpApp(app)
    client = HuggingFaceClient.withRetry(raw, maxRetries = 3, maxWait = 10.millis)
  } yield (HuggingFaceClient(client, Uri.unsafeFromString("http://localhost")), counter)

  it should "retry on HTTP 429" in {
    val (bytes, calls) = (for {
      (client, counter) <- flakyClient(failures = 2, failStatus = Status.TooManyRequests)
      bytes             <- client.modelFile(ModelHandle("metarank", "all-MiniLM-L6-v2"), "vocab.txt")
      calls             <- counter.get
    } yield (bytes, calls)).unsafeRunSync()
    new String(bytes) shouldBe "ok"
    calls shouldBe 3
  }

  it should "give up after max retries" in {
    val (result, calls) = (for {
      (client, counter) <- flakyClient(failures = 100, failStatus = Status.TooManyRequests)
      result            <- client.modelFile(ModelHandle("metarank", "all-MiniLM-L6-v2"), "vocab.txt").attempt
      calls             <- counter.get
    } yield (result, calls)).unsafeRunSync()
    result.left.map(_.getMessage) shouldBe Left("HTTP code 429")
    calls shouldBe 4 // 1 attempt + 3 retries
  }

  it should "not retry on HTTP 404" in {
    val (result, calls) = (for {
      (client, counter) <- flakyClient(failures = 100, failStatus = Status.NotFound)
      result            <- client.modelFile(ModelHandle("metarank", "all-MiniLM-L6-v2"), "vocab.txt").attempt
      calls             <- counter.get
    } yield (result, calls)).unsafeRunSync()
    result.left.map(_.getMessage) shouldBe Left("HTTP code 404")
    calls shouldBe 1
  }

  it should "fetch metadata" in {
    val model = HuggingFaceClient
      .create()
      .use(client => client.model(ModelHandle("metarank", "all-MiniLM-L6-v2")))
      .unsafeRunSync()
    model.siblings should contain(Sibling("vocab.txt"))
  }

  it should "fetch files" in {
    val vocab =
      HuggingFaceClient
        .create()
        .use(_.modelFile(ModelHandle("metarank", "all-MiniLM-L6-v2"), "vocab.txt"))
        .unsafeRunSync()
    vocab.length should be > (100)
  }
}
