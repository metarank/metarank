package ai.metarank.main.api

import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.util.{TestConfig, TestFeatureMapping}
import cats.effect.unsafe.implicits.global
import org.http4s._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HealthApiTest extends AnyFlatSpec with Matchers {
  lazy val mapping = TestFeatureMapping()
  lazy val api     = HealthApi(MemPersistence(mapping.schema))

  it should "make healthcheck" in {
    val response = api.routes(Request(uri = Uri.unsafeFromString("http://localhost/health"))).value.unsafeRunSync()
    response.map(_.status.code) shouldBe Some(200)
  }
}
