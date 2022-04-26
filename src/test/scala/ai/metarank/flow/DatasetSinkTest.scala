package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.config.Config.ModelConfig.{LambdaMARTConfig, XGBoostBackend}
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.model.Clickthrough.ItemValues
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.{Clickthrough, EventId, FieldName}
import ai.metarank.model.FieldName.EventType.{Interaction, Item}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.{FlinkTest, TestInteractionEvent, TestRankingEvent}
import better.files.File
import cats.data.{NonEmptyList, NonEmptyMap}
import org.apache.flink.api.common.RuntimeExecutionMode
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.apache.flink.api.scala._
import org.apache.lucene.search.similarities.Lambda

class DatasetSinkTest extends AnyFlatSpec with Matchers with FlinkTest {
  it should "write cts" in {
    env.setRuntimeMode(RuntimeExecutionMode.BATCH)
    val model = LambdaMARTConfig(XGBoostBackend, NonEmptyList.of("budget"), NonEmptyMap.of("click" -> 1))
    val mapping = FeatureMapping.fromFeatureSchema(
      schema = NonEmptyList.of(NumberFeatureSchema("budget", FieldName(Item, "budget"), ItemScope)),
      models = NonEmptyMap.of("test" -> model)
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
    env.fromCollection(List(ct)).sinkTo(DatasetSink.csv(mapping.models("test").datasetDescriptor, s"file:///$dir"))
    env.execute()
    val csv = dir.listRecursively.filter(_.name.endsWith(".csv")).toList.headOption.map(_.contentAsString)
    val expected = """label,group,budget
                     |1.0,49,1.0
                     |0.0,49,0.0
                     |""".stripMargin
    csv shouldBe Some(expected)
  }
}
