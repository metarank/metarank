package ai.metarank.feature

import ai.metarank.feature.NumVectorFeature.Reducer._
import ai.metarank.feature.NumVectorFeature.VectorFeatureSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Field.NumberListField
import ai.metarank.model.{FieldName, Key}
import ai.metarank.model.FieldName.EventType
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.Scalar.SDoubleList
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.Write.Put
import ai.metarank.util.{TestItemEvent, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.parse

class NumVectorFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {
  val feature = NumVectorFeature(
    VectorFeatureSchema(
      name = FeatureName("vec"),
      source = FieldName(EventType.Item, "vec"),
      scope = ItemScopeType
    )
  )

  it should "decode config with no reducers" in {
    val yaml =
      """name: vec
        |source: item.vec
        |scope: item""".stripMargin
    parse(yaml).flatMap(_.as[VectorFeatureSchema]) shouldBe Right(
      VectorFeatureSchema(
        name = FeatureName("vec"),
        source = FieldName(EventType.Item, "vec"),
        scope = ItemScopeType
      )
    )
  }

  it should "decode config with reducers" in {
    val yaml =
      """name: vec
        |source: item.vec
        |reduce: [min, max, avg]
        |scope: item""".stripMargin
    parse(yaml).flatMap(_.as[VectorFeatureSchema]) shouldBe Right(
      VectorFeatureSchema(
        name = FeatureName("vec"),
        source = FieldName(EventType.Item, "vec"),
        reduce = Some(List(Min, Max, Avg)),
        scope = ItemScopeType
      )
    )
  }

  it should "extract field from metadata with default reducers" in {
    val event  = TestItemEvent("p1", List(NumberListField("vec", List(1.0, 2.0, 3.0))))
    val result = feature.writes(event, Persistence.blackhole()).unsafeRunSync().toList
    result shouldBe List(
      Put(Key(ItemScope(ItemId("p1")), FeatureName("vec")), event.timestamp, SDoubleList(List(1.0, 3.0, 3.0, 2.0)))
    )
  }

  it should "compute value" in {
    val values = process(
      List(TestItemEvent("p1", List(NumberListField("vec", List(1.0, 2.0, 3.0))))),
      feature.schema,
      TestRankingEvent(List("p1"))
    )
    values shouldBe List(List(VectorValue(FeatureName("vec"), Array(1.0, 3.0, 3.0, 2.0), 4)))
  }

}
