package ai.metarank.feature

import ai.metarank.feature.DiversityFeature.DiversitySchema
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.Field.{NumberField, StringField, StringListField}
import ai.metarank.model.{FieldName, Schema}
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.{TestItemEvent, TestRankingEvent}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DiversityFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {

  it should "compute diversity over numbers" in {
    val conf = DiversitySchema(
      name = FeatureName("divnum"),
      source = FieldName(Item, "price")
    )
    val events = List(
      TestItemEvent("p1", List(NumberField("price", 10.0))),
      TestItemEvent("p2", List(NumberField("price", 20.0))),
      TestItemEvent("p3", List(NumberField("price", 40.0))),
      TestItemEvent("p4", List(NumberField("price", 15.0))),
      TestItemEvent("p5", List(NumberField("price", 5.0)))
    )
    val result = process(events, conf, TestRankingEvent(List("p1", "p2", "p3", "p4", "p5"))).flatten
    result shouldBe List(
      SingleValue(conf.name, -5.0),
      SingleValue(conf.name, 5.0),
      SingleValue(conf.name, 25.0),
      SingleValue(conf.name, 0.0),
      SingleValue(conf.name, -10.0)
    )
  }

  it should "compute diversity over topN numbers" in {
    val conf = DiversitySchema(
      name = FeatureName("divnum"),
      source = FieldName(Item, "price"),
      top = 3
    )
    val events = List(
      TestItemEvent("p1", List(NumberField("price", 10.0))),
      TestItemEvent("p2", List(NumberField("price", 20.0))),
      TestItemEvent("p3", List(NumberField("price", 30.0))),
      TestItemEvent("p4", List(NumberField("price", 5.0))),
      TestItemEvent("p5", List(NumberField("price", 1.0)))
    )
    val result = process(events, conf, TestRankingEvent(List("p1", "p2", "p3", "p4", "p5"))).flatten
    result shouldBe List(
      SingleValue(conf.name, -10.0),
      SingleValue(conf.name, 0.0),
      SingleValue(conf.name, 10.0),
      SingleValue(conf.name, -15.0),
      SingleValue(conf.name, -19.0)
    )
  }

  it should "compute diversity over strings" in {
    val conf = DiversitySchema(
      name = FeatureName("divstr"),
      source = FieldName(Item, "cat")
    )
    val events = List(
      TestItemEvent("p1", List(StringField("cat", "a"))),
      TestItemEvent("p2", List(StringField("cat", "b"))),
      TestItemEvent("p3", List(StringField("cat", "c"))),
      TestItemEvent("p4", List(StringField("cat", "a"))),
      TestItemEvent("p5", List(StringField("cat", "b")))
    )
    val result = process(events, conf, TestRankingEvent(List("p1", "p2", "p3", "p4", "p5"))).flatten
    result shouldBe List(
      SingleValue(conf.name, 0.4),
      SingleValue(conf.name, 0.4),
      SingleValue(conf.name, 0.2),
      SingleValue(conf.name, 0.4),
      SingleValue(conf.name, 0.4)
    )
  }
  it should "compute diversity over string lists" in {

    val conf = DiversitySchema(
      name = FeatureName("divstrl"),
      source = FieldName(Item, "cat")
    )
    val events = List(
      TestItemEvent("p1", List(StringListField("cat", List("a")))),
      TestItemEvent("p2", List(StringListField("cat", List("b", "c")))),
      TestItemEvent("p3", List(StringListField("cat", List("a", "b", "c")))),
      TestItemEvent("p4", List(StringListField("cat", List("a", "b", "c", "d"))))
    )
    val result = process(events, conf, TestRankingEvent(List("p1", "p2", "p3", "p4"))).flatten
    result shouldBe List(
      SingleValue(conf.name, 0.3),
      SingleValue(conf.name, 0.6),
      SingleValue(conf.name, 0.9),
      SingleValue(conf.name, 1.0)
    )
  }
}
