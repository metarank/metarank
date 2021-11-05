package ai.metarank.model

import ai.metarank.model.Event.{ImpressionEvent, InteractionEvent, ItemRelevancy, MetadataEvent}
import ai.metarank.model.Field.{BooleanField, NumberField, NumberListField, StringField, StringListField}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser._
import io.findify.featury.model.Timestamp

class EventJsonTest extends AnyFlatSpec with Matchers {
  it should "decode metadata" in {
    val json = """{
                 |  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
                 |  "item": "product1",
                 |  "timestamp": "1599391467000", 
                 |  "fields": [
                 |    {"name": "title", "value": "Nice jeans"},
                 |    {"name": "price", "value": 25.0},
                 |    {"name": "color", "value": ["blue", "black"]},
                 |    {"name": "availability", "value": true}
                 |  ]
                 |}""".stripMargin
    decode[Event](json) shouldBe Right(
      MetadataEvent(
        id = EventId("81f46c34-a4bb-469c-8708-f8127cd67d27"),
        item = ItemId("product1"),
        timestamp = Timestamp(1599391467000L),
        fields = List(
          StringField("title", "Nice jeans"),
          NumberField("price", 25),
          StringListField("color", List("blue", "black")),
          BooleanField("availability", true)
        )
      )
    )
  }

  it should "decode impressions" in {
    val json = """{
                 |  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
                 |  "timestamp": "1599391467000",
                 |  "user": "user1",
                 |  "session": "session1",
                 |  "fields": [
                 |      {"name": "query", "value": "jeans"},
                 |      {"name": "source", "value": "search"}
                 |  ],
                 |  "items": [
                 |    {"id": "product3", "relevancy":  2.0},
                 |    {"id": "product1", "relevancy":  1.0},
                 |    {"id": "product2", "relevancy":  0.5} 
                 |  ]
                 |}
                 |""".stripMargin
    decode[Event](json) shouldBe Right(
      ImpressionEvent(
        id = EventId("81f46c34-a4bb-469c-8708-f8127cd67d27"),
        timestamp = Timestamp(1599391467000L),
        user = UserId("user1"),
        session = SessionId("session1"),
        fields = List(
          StringField("query", "jeans"),
          StringField("source", "search")
        ),
        items = List(
          ItemRelevancy(ItemId("product3"), 2.0),
          ItemRelevancy(ItemId("product1"), 1.0),
          ItemRelevancy(ItemId("product2"), 0.5)
        )
      )
    )
  }

  it should "decode interactions" in {
    val json = """{
                 |  "id": "0f4c0036-04fb-4409-b2c6-7163a59f6b7d",
                 |  "impression": "81f46c34-a4bb-469c-8708-f8127cd67d27",
                 |  "timestamp": "1599391467000",
                 |  "user": "user1",
                 |  "session": "session1",
                 |  "type": "purchase",
                 |  "item": "product1",
                 |  "fields": [
                 |    {"name": "count", "value": 2},
                 |    {"name": "shipping", "value": "DHL"}
                 |  ]
                 |}""".stripMargin
    decode[Event](json) shouldBe Right(
      InteractionEvent(
        id = EventId("0f4c0036-04fb-4409-b2c6-7163a59f6b7d"),
        impression = EventId("81f46c34-a4bb-469c-8708-f8127cd67d27"),
        timestamp = Timestamp(1599391467000L),
        user = UserId("user1"),
        session = SessionId("session1"),
        `type` = "purchase",
        item = ItemId("product1"),
        fields = List(
          NumberField("count", 2),
          StringField("shipping", "DHL")
        )
      )
    )
  }
}
