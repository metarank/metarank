package me.dfdx.metarank.service

import cats.data.NonEmptyList
import io.circe.Encoder
import me.dfdx.metarank.{Aggregations, TestAggregation}
import me.dfdx.metarank.services.{HealthcheckService, IngestService}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import me.dfdx.metarank.model.Event.{ClickEvent, ItemMetadataEvent, RankEvent, RankItem}
import me.dfdx.metarank.model.Field.StringField
import me.dfdx.metarank.model.{
  Event,
  Featurespace,
  ItemId,
  Nel,
  TestClickEvent,
  TestConversionEvent,
  TestRankEvent,
  Timestamp
}

class IngestServiceTest extends AnyFlatSpec with Matchers with ServiceRouteTest {
  val item   = ItemMetadataEvent(ItemId("a"), Timestamp(1L), NonEmptyList.one(StringField("foo", "bar")))
  val ingest = IngestService(Featurespace("dev"), Aggregations(Nel(TestAggregation)))

  it should "push single item update" in {
    post(ingest.route, item, "/dev/ingest/item").map(_.status.code) shouldBe Some(200)
  }

  it should "push batch item update" in {
    implicit val encoder = Encoder.encodeList(Event.itemMetadataCodec)
    post(ingest.route, List(item, item), "/dev/ingest/item/batch").map(_.status.code) shouldBe Some(200)
  }

  it should "push rank update" in {
    val rank = TestRankEvent(ItemId("p1"), "fox")
    post(ingest.route, rank, "/dev/ingest/rank").map(_.status.code) shouldBe Some(200)
  }

  it should "push click" in {
    val click = TestClickEvent(ItemId("p1"))
    post(ingest.route, click, "/dev/ingest/click").map(_.status.code) shouldBe Some(200)
  }

  it should "push conversion" in {
    val conv = TestConversionEvent(ItemId("p1"))
    post(ingest.route, conv, "/dev/ingest/conv").map(_.status.code) shouldBe Some(200)
  }

  it should "respond with error on wrong path" in {
    val click = TestClickEvent(ItemId("p1"))
    post(ingest.route, click, "/foo/ingest/click").map(_.status.code) shouldBe None
  }

}
