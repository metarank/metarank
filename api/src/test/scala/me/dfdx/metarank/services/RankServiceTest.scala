package me.dfdx.metarank.services

import cats.data.NonEmptyList
import me.dfdx.metarank.model.Event.RankItem
import me.dfdx.metarank.model.{ItemId, RankRequest, RequestId, Scope, SessionId, Timestamp, UserId}
import me.dfdx.metarank.service.ServiceRouteTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Try

class RankServiceTest extends AnyFlatSpec with Matchers with ServiceRouteTest {
  it should "respond with items" in {
    val request = RankRequest(
      id = RequestId("p"),
      timestamp = Timestamp(1L),
      scope = Scope(user = UserId("u"), session = SessionId("s"), fields = Nil),
      items = NonEmptyList.one(RankItem(ItemId("p1"), 1.0))
    )
    val response = post(RankService.route, request, "/rank")
    response.map(_.status.code) shouldBe Some(200)
  }

  it should "fail on broken events" in {
    val request = RankRequest(
      id = RequestId("p"),
      timestamp = Timestamp(-100L),
      scope = Scope(user = UserId(""), session = SessionId("s"), fields = Nil),
      items = NonEmptyList.one(RankItem(ItemId("p1"), 1.0))
    )
    val response = Try(post(RankService.route, request, "/rank"))
    response.isFailure shouldBe true
  }
}
