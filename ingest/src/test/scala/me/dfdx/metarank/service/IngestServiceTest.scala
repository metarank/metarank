package me.dfdx.metarank.service

import cats.data.NonEmptyList
import cats.effect.IO
import io.circe.Encoder
import me.dfdx.metarank.services.{HealthcheckService, IngestService}
import org.http4s.{Method, Request, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import me.dfdx.metarank.model.Event.{InteractionEvent, ItemMetadataEvent, RankEvent, RankItem}
import me.dfdx.metarank.model.Field.StringField
import me.dfdx.metarank.model.{Event, ItemId, RequestId, Timestamp}

class IngestServiceTest extends AnyFlatSpec with Matchers {
  val item = ItemMetadataEvent(ItemId("a"), Timestamp(1L), NonEmptyList.one(StringField("foo", "bar")))
  val rank = RankEvent(RequestId("a"), Timestamp(1L), NonEmptyList.of(RankItem(ItemId("p1"), 1.0)))
  val int  = InteractionEvent(RequestId("a"), Timestamp(1L), "fpp", ItemId("p1"))

  it should "push single item update" in {
    push(item, "/ingest/item").map(_.status.code) shouldBe Some(200)
  }

  it should "push batch item update" in {
    implicit val encoder = Encoder.encodeList(Event.itemMetadataCodec)
    push(List(item, item), "/ingest/item/batch").map(_.status.code) shouldBe Some(200)
  }

  it should "push rank update" in {
    push(rank, "/ingest/rank").map(_.status.code) shouldBe Some(200)
  }

  it should "push interaction" in {
    push(int, "/ingest/interaction").map(_.status.code) shouldBe Some(200)
  }

  def push[T](event: T, path: String)(implicit encoder: Encoder[T]) = {
    val request =
      Request[IO](uri = Uri.unsafeFromString(path), method = Method.POST, body = makeBody(event))
    IngestService.route.run(request).value.unsafeRunSync()
  }

  def makeBody[T](event: T)(implicit encoder: Encoder[T]): fs2.Stream[IO, Byte] = {
    val bytes = event.asJson.noSpaces.getBytes()
    fs2.Stream(bytes: _*)
  }
}
