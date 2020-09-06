package me.dfdx.metarank.service

import cats.effect._
import me.dfdx.metarank.services.HealthcheckService
import org.http4s.{Request, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HealthcheckServiceTest extends AnyFlatSpec with Matchers with ServiceRouteTest {
  it should "return 200" in {
    val response = get(HealthcheckService.route, "/health")
    response.map(_.status.code) shouldBe Some(200)
  }
}
