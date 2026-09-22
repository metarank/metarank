package ai.metarank.main.api

import ai.metarank.api.routes.MetricsApi
import ai.metarank.util.analytics.Metrics
import cats.effect.unsafe.implicits.global
import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.http4s.{Request, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MetricsApiTest extends AnyFlatSpec with Matchers {
  lazy val api = MetricsApi(PrometheusRegistry.defaultRegistry)
  it should "fetch metrics" in {
    val response = api.routes(Request(uri = Uri.unsafeFromString("http://localhost/metrics"))).value.unsafeRunSync()
    response.map(_.status.code) shouldBe Some(200)
  }

  it should "expose metarank metrics in text format" in {
    Metrics.requests.labelValues("test").inc()
    val response = api.routes(Request(uri = Uri.unsafeFromString("http://localhost/metrics"))).value.unsafeRunSync()
    val body     = response.map(_.as[String].unsafeRunSync()).getOrElse("")
    body should include("""metarank_rank_requests_total{model="test"}""")
  }
}
