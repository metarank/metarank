package me.dfdx.metarank.service

import cats.data.NonEmptyList
import cats.effect.IO
import io.circe.Encoder
import me.dfdx.metarank.services.{HealthcheckService, IngestService}
import org.http4s.{Method, Request, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import me.dfdx.metarank.config.Config.InteractionType
import me.dfdx.metarank.model.Event.{InteractionEvent, ItemMetadataEvent, RankEvent, RankItem}
import me.dfdx.metarank.model.Field.StringField
import me.dfdx.metarank.model.{Event, ItemId, RequestId, Scope, SessionId, Timestamp, UserId}

class IngestServiceTest extends AnyFlatSpec with Matchers with ServiceRouteTest {
  val item = ItemMetadataEvent(ItemId("a"), Timestamp(1L), NonEmptyList.one(StringField("foo", "bar")))
  val rank = RankEvent(RequestId("a"), Timestamp(1L), NonEmptyList.of(RankItem(ItemId("p1"), 1.0)))
  val int =
    InteractionEvent(RequestId("a"), Timestamp(1L), UserId("u"), SessionId("s"), InteractionType("fpp"), ItemId("p1"))

  it should "push single item update" in {
    post(IngestService.route, item, "/ingest/item").map(_.status.code) shouldBe Some(200)
  }

  it should "push batch item update" in {
    implicit val encoder = Encoder.encodeList(Event.itemMetadataCodec)
    post(IngestService.route, List(item, item), "/ingest/item/batch").map(_.status.code) shouldBe Some(200)
  }

  it should "push rank update" in {
    post(IngestService.route, rank, "/ingest/rank").map(_.status.code) shouldBe Some(200)
  }

  it should "push interaction" in {
    post(IngestService.route, int, "/ingest/interaction").map(_.status.code) shouldBe Some(200)
  }

}
