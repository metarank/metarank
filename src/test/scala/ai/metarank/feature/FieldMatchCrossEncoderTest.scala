package ai.metarank.feature

import ai.metarank.feature.FieldMatchCrossEncoderFeature.FieldMatchCrossEncoderSchema
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.ml.onnx.ModelHandle
import ai.metarank.ml.onnx.distance.DistanceFunction.CosineDistance
import ai.metarank.ml.onnx.encoder.EncoderConfig.CrossEncoderConfig
import ai.metarank.model.Event.ItemEvent
import ai.metarank.model.Field.StringField
import ai.metarank.model.{EventId, FeatureSchema, FieldName, Timestamp, Write}
import ai.metarank.model.FieldName.EventType.{Item, Ranking}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.Scalar.SString
import ai.metarank.util.{TestRankingEvent, TestSchema}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FieldMatchCrossEncoderTest extends AnyFlatSpec with Matchers with FeatureTest {
  val schema = FieldMatchCrossEncoderSchema(
    name = FeatureName("foo"),
    rankingField = FieldName(Ranking, "query"),
    itemField = FieldName(Item, "title"),
    distance = CosineDistance,
    method = CrossEncoderConfig(Some(ModelHandle("metarank", "ce-msmarco-MiniLM-L6-v2")), dim = 384)
  )
  lazy val feature = schema.create().unsafeRunSync().asInstanceOf[FieldMatchCrossEncoderFeature]

  val now = Timestamp.now
  val itemEvent = ItemEvent(
    id = EventId("1"),
    item = ItemId("p1"),
    timestamp = now,
    fields = List(StringField("title", "red socks"))
  )

  it should "decode config" in {
    val yaml =
      """type: field_match
        |name: foo
        |rankingField: ranking.query
        |itemField: item.title
        |distance: cosine
        |method:
        |  type: cross-encoder
        |  dim: 384
        |  model: metarank/ce-msmarco-MiniLM-L6-v2
        |  """.stripMargin
    val decoded = io.circe.yaml.parser.parse(yaml).flatMap(_.as[FeatureSchema])
    decoded shouldBe Right(schema)
  }
  it should "generate puts on item events" in {
    val store = MemPersistence(TestSchema(schema))
    val value =
      feature.writes(itemEvent, store).unsafeRunSync().collectFirst { case Write.Put(_, _, SString(value)) => value }
    value shouldBe Some("red socks")
  }

  it should "generate values" in {
    val item2 = itemEvent.copy(item = ItemId("p2"), fields = List(StringField("title", "red socks")))
    val item3 = itemEvent.copy(item = ItemId("p3"), fields = List(StringField("title", "green socks")))
    val item4 = itemEvent.copy(item = ItemId("p4"), fields = List(StringField("title", "your mom")))
    val result = process(
      List(item2, item3, item4),
      schema,
      TestRankingEvent(List("p2", "p3", "p4")).copy(fields = List(StringField("query", "santa socks")))
    ).flatten.collect { case SingleValue(_, value) => value }.toArray
    result(0) shouldBe 0.02509 +- 0.001
    result(1) shouldBe 0.01512 +- 0.001
    result(2) shouldBe 0.00016 +- 0.001
  }

}
