package me.dfdx.metarank.services

import cats.data.NonEmptyList
import me.dfdx.metarank.model.Event.RankItem
import me.dfdx.metarank.model.{ItemId, RankRequest, RequestId, SessionId, TestMetadata, Timestamp, UserId}
import me.dfdx.metarank.service.ServiceRouteTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Try

class RankServiceTest extends AnyFlatSpec with Matchers with ServiceRouteTest {
  it should "respond with items" in {
    val request = RankRequest(
      id = RequestId("p"),
      items = NonEmptyList.one(RankItem(ItemId("p1"), 1.0)),
      metadata = TestMetadata()
    )
    val response = post(RankService.route, request, "/rank")
    response.map(_.status.code) shouldBe Some(200)
  }

  it should "fail on broken events" in {
    val request = RankRequest(
      id = RequestId("p"),
      items = NonEmptyList.one(RankItem(ItemId(""), 1.0)),
      metadata = TestMetadata()
    )
    val response = Try(post(RankService.route, request, "/rank"))
    response.isFailure shouldBe true
  }
}
