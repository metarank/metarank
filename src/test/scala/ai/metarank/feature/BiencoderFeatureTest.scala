package ai.metarank.feature

import ai.metarank.feature.BiencoderFeature.BiencoderSchema
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.ml.onnx.distance.DistanceFunction.CosineDistance
import ai.metarank.ml.onnx.encoder.EncoderType.BertEncoderType
import ai.metarank.model.Event.ItemEvent
import ai.metarank.model.Field.StringField
import ai.metarank.model.{EventId, FeatureSchema, Timestamp, Write}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.Scalar.SDoubleList
import ai.metarank.util.{TestRankingEvent, TestSchema}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BiencoderFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {
  val schema = BiencoderSchema(
    name = FeatureName("foo"),
    rankingField = "query",
    itemField = "title",
    distance = CosineDistance,
    encoder = BertEncoderType("sentence-transformer/all-MiniLM-L6-v2")
  )
  lazy val feature = schema.create().unsafeRunSync().asInstanceOf[BiencoderFeature]

  val now = Timestamp.now
  val itemEvent = ItemEvent(
    id = EventId("1"),
    item = ItemId("p1"),
    timestamp = now,
    fields = List(StringField("title", "red socks"))
  )

  it should "decode config" in {
    val yaml =
      """type: biencoder
        |name: foo
        |rankingField: query
        |itemField: title
        |distance: cosine
        |encoder:
        |  type: bert
        |  model: sentence-transformer/all-MiniLM-L6-v2
        |  """.stripMargin
    val decoded = io.circe.yaml.parser.parse(yaml).flatMap(_.as[FeatureSchema])
    decoded shouldBe Right(schema)
  }

  it should "generate puts on item events" in {
    val store = MemPersistence(TestSchema(schema))
    val emb =
      feature.writes(itemEvent, store).unsafeRunSync().collectFirst { case Write.Put(_, _, SDoubleList(value)) =>
        value
      }
    emb.map(_.length) shouldBe Some(384)
  }

  it should "generate values" in {
    val item2 = itemEvent.copy(item = ItemId("p2"), fields = List(StringField("title", "red socks")))
    val item3 = itemEvent.copy(item = ItemId("p3"), fields = List(StringField("title", "green socks")))
    val item4 = itemEvent.copy(item = ItemId("p4"), fields = List(StringField("title", "your mom")))
    val result = process(
      List(item2, item3, item4),
      schema,
      TestRankingEvent(List("p2", "p3", "p4")).copy(fields = List(StringField("query", "santa socks")))
    )
    result shouldBe List(
      List(
        SingleValue(schema.name, 0.7093414519478631),
        SingleValue(schema.name, 0.651140896691446),
        SingleValue(schema.name, 0.2450188892872221)
      )
    )
  }
}
