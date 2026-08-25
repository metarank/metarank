package ai.metarank.model

import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankItem, RankingEvent}
import ai.metarank.model.Field.{BooleanField, NumberField, StringField, StringListField}
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import better.files.Resource
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*
import io.circe.syntax.*

class EventJsonTest extends AnyFlatSpec with Matchers {
  it should "decode item metadata" in {
    val json = """{
                 |  "event": "metadata",
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
      ItemEvent(
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

  it should "decode item metadata with type=item" in {
    val json = """{
                 |  "event": "item",
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
      ItemEvent(
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

  it should "decode metadata with empty fields" in {
    val json = """{
                 |  "event": "metadata",
                 |  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
                 |  "item": "product1",
                 |  "timestamp": "1599391467000"
                 |}""".stripMargin
    decode[Event](json) shouldBe Right(
      ItemEvent(
        id = EventId("81f46c34-a4bb-469c-8708-f8127cd67d27"),
        item = ItemId("product1"),
        timestamp = Timestamp(1599391467000L),
        fields = Nil
      )
    )
  }

  it should "decode ranking" in {
    val json = """{
                 |  "event": "ranking",
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
      RankingEvent(
        id = EventId("81f46c34-a4bb-469c-8708-f8127cd67d27"),
        timestamp = Timestamp(1599391467000L),
        user = Some(UserId("user1")),
        session = Some(SessionId("session1")),
        fields = List(
          StringField("query", "jeans"),
          StringField("source", "search")
        ),
        items = NonEmptyList.of(
          RankItem(ItemId("product3"), 2.0),
          RankItem(ItemId("product1"), 1.0),
          RankItem(ItemId("product2"), 0.5)
        )
      )
    )
  }

  it should "decode interactions" in {
    val json = """{
                 |  "event": "interaction",
                 |  "id": "0f4c0036-04fb-4409-b2c6-7163a59f6b7d",
                 |  "ranking": "81f46c34-a4bb-469c-8708-f8127cd67d27",
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
        ranking = Some(EventId("81f46c34-a4bb-469c-8708-f8127cd67d27")),
        timestamp = Timestamp(1599391467000L),
        user = Some(UserId("user1")),
        session = Some(SessionId("session1")),
        `type` = "purchase",
        item = ItemId("product1"),
        fields = List(
          NumberField("count", 2),
          StringField("shipping", "DHL")
        )
      )
    )
  }

  it should "decode timestamp as long/string/iso" in {
    import Event.EventCodecs.given
    decode[Timestamp]("123") shouldBe Right(Timestamp(123L))
    decode[Timestamp]("\"123\"") shouldBe Right(Timestamp(123L))
    decode[Timestamp]("\"2022-06-22T11:21:39Z\"") shouldBe Right(Timestamp(1655896899000L))
  }

  // encode snapshots: the JSON shape is consumed by external clients and persisted event files, so it must stay stable
  it should "encode item events into the reference json" in {
    val event: Event = ItemEvent(
      id = EventId("e1"),
      item = ItemId("p1"),
      timestamp = Timestamp(1599391467000L),
      fields = List(StringField("title", "Nice jeans"), NumberField("price", 25.0))
    )
    val expected = parse(
      """{"id":"e1","item":"p1","timestamp":"1599391467000",
        |"fields":[{"name":"title","value":"Nice jeans"},{"name":"price","value":25.0}],
        |"event":"item"}""".stripMargin
    )
    Right(event.asJson) shouldBe expected
  }

  it should "encode interaction events into the reference json" in {
    val event: Event = InteractionEvent(
      id = EventId("e2"),
      item = ItemId("p1"),
      timestamp = Timestamp(1599391467000L),
      ranking = Some(EventId("e1")),
      user = Some(UserId("u1")),
      session = Some(SessionId("s1")),
      `type` = "click",
      fields = List(NumberField("count", 2.0))
    )
    val expected = parse(
      """{"id":"e2","item":"p1","timestamp":"1599391467000","ranking":"e1","user":"u1","session":"s1",
        |"type":"click","fields":[{"name":"count","value":2.0}],
        |"event":"interaction"}""".stripMargin
    )
    Right(event.asJson) shouldBe expected
  }

  it should "encode ranking events into the reference json" in {
    val event: Event = RankingEvent(
      id = EventId("e3"),
      timestamp = Timestamp(1599391467000L),
      user = Some(UserId("u1")),
      session = None,
      fields = List(StringField("query", "jeans")),
      items = NonEmptyList.of(RankItem(ItemId("p1"), 1.0))
    )
    val expected = parse(
      """{"id":"e3","timestamp":"1599391467000","user":"u1","session":null,
        |"fields":[{"name":"query","value":"jeans"}],
        |"items":[{"id":"p1","fields":[{"name":"relevancy","value":1.0}],"label":null}],
        |"event":"ranking"}""".stripMargin
    )
    Right(event.asJson) shouldBe expected
  }

  it should "decode japanese in field values" in {
    val json    = Resource.my.getAsString("/japanese.json")
    val decoded = decode[Event](json)
    decoded shouldBe Right(
      ItemEvent(
        id = EventId("beb21c70-b3ef-4fc8-9ded-e9e93707371a"),
        timestamp = Timestamp(1679007381000L),
        item = ItemId("a071m00000370MKAAY"),
        fields = List(StringField("maker", "ﾒｲｽﾞ ｵﾘｼﾞﾅﾙ"))
      )
    )
  }
}
