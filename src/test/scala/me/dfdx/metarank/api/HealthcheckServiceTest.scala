package me.dfdx.metarank.api

import cats.effect._
import org.http4s.{Request, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HealthcheckServiceTest extends AnyFlatSpec with Matchers {
  it should "return 200" in {
    val request  = Request[IO](uri = Uri.unsafeFromString("/health"))
    val response = HealthcheckService.route.run(request).value.unsafeRunSync()
    response.map(_.status.code) shouldBe Some(200)
  }
}
