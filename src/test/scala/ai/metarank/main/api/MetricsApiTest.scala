package ai.metarank.main.api

import ai.metarank.api.routes.MetricsApi
import cats.effect.unsafe.implicits.global
import io.prometheus.client.CollectorRegistry
import org.http4s.{Request, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MetricsApiTest extends AnyFlatSpec with Matchers {
  lazy val api = MetricsApi(CollectorRegistry.defaultRegistry)
  it should "fetch metrics" in {
    val response = api.routes(Request(uri = Uri.unsafeFromString("http://localhost/metrics"))).value.unsafeRunSync()
    response.map(_.status.code) shouldBe Some(200)
  }
}
