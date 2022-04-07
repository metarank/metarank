package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.config.Config.InteractionConfig
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.model.Clickthrough.ItemValues
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.{Clickthrough, EventId, FieldName}
import ai.metarank.model.FieldName.{Interaction, Item}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.{FlinkTest, TestInteractionEvent, TestRankingEvent}
import better.files.File
import org.apache.flink.api.common.RuntimeExecutionMode
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.apache.flink.api.scala._

class DatasetSinkTest extends AnyFlatSpec with Matchers with FlinkTest {
  it should "write cts" in {
    env.setRuntimeMode(RuntimeExecutionMode.BATCH)
    val mapping = FeatureMapping.fromFeatureSchema(
      schema = List(NumberFeatureSchema("budget", FieldName(Item, "budget"), ItemScope)),
      interactions = List(InteractionConfig("click", 1.0))
    )
    val ct = Clickthrough(
      ranking = TestRankingEvent(List("p1", "p2")).copy(id = EventId("1")),
      interactions = Nil,
      values = List(
        ItemValues(ItemId("p1"), 1.0, List(SingleValue("budget", 1))),
        ItemValues(ItemId("p2"), 0.0, List(SingleValue("budget", 0)))
      )
    )
    val dir = File.newTemporaryDirectory("csv_")
    env.fromCollection(List(ct)).sinkTo(DatasetSink.csv(mapping, s"file:///$dir"))
    env.execute()
    val csv = dir.listRecursively.filter(_.name.endsWith(".csv")).toList.headOption.map(_.contentAsString)
    val expected = """label,group,budget
                     |1.0,49,1.0
                     |0.0,49,0.0
                     |""".stripMargin
    csv shouldBe Some(expected)
  }
}
