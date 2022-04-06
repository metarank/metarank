package ai.metarank.e2e

import ai.metarank.FeatureMapping
import ai.metarank.config.Config.InteractionConfig
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.flow.EventStateJoin
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope}
import ai.metarank.model.Field.StringField
import ai.metarank.model.{Event, EventState, FieldName}
import ai.metarank.model.FieldName.Item
import ai.metarank.util.{FlinkTest, TestInteractionEvent, TestMetadataEvent}
import io.findify.featury.flink.Featury
import io.findify.featury.model.{BoundedListValue, SString, Timestamp}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.findify.flinkadt.api._
import org.apache.flink.api.common.RuntimeExecutionMode

import scala.language.higherKinds
import scala.concurrent.duration._

class StatefulFeatureTest extends AnyFlatSpec with Matchers with FlinkTest {
  import ai.metarank.mode.TypeInfos._
  import ai.metarank.flow.DataStreamOps._
  val features = List(
    InteractedWithSchema(
      "clicked_color",
      "click",
      FieldName(Item, "color"),
      SessionScope,
      Some(10),
      Some(24.hours)
    )
  )

  val inters = List(InteractionConfig("click", 1.0))

//  it should "process stateful features" in {
//    env.setRuntimeMode(RuntimeExecutionMode.BATCH)
//    val now     = Timestamp.now
//    val mapping = FeatureMapping.fromFeatureSchema(features, inters)
//    val events = env
//      .fromCollection(
//        List[Event](
//          TestMetadataEvent("p1", List(StringField("color", "red"))).copy(timestamp = now.plus(1.minute)),
//          TestMetadataEvent("p2", List(StringField("color", "green"))).copy(timestamp = now.plus(2.minute)),
//          TestMetadataEvent("p3", List(StringField("color", "blue"))).copy(timestamp = now.plus(3.minute)),
//          TestInteractionEvent("p1", "x").copy(timestamp = now.plus(4.minute)),
//          TestInteractionEvent("p3", "x").copy(timestamp = now.plus(5.minute))
//        )
//      )
//      .id("source")
//      .watermark(_.timestamp.ts)
//    val statelessWrites  = events.flatMap(e => mapping.features.flatMap(_.writes(e)))
//    val statelessUpdates = Featury.process(statelessWrites, mapping.schema, 20.seconds).watermark(_.ts.ts)
//
//    val eventsWithState =
//      Featury
//        .join[EventState](statelessUpdates, events.map(e => EventState(e)), EventStateJoin, mapping.statefulSchema)
//    val statefulWrites  = eventsWithState.flatMap(e => mapping.statefulFeatures.flatMap(_.writes(e.event, e.state)))
//    val statefulUpdates = Featury.process(statefulWrites, mapping.statefulSchema, 20.seconds)
//
//    val result = statefulUpdates.executeAndCollect(10)
//    val lists = result.collect { case BoundedListValue(_, _, values) =>
//      values.map(_.value)
//    }
//    lists shouldBe List(List(SString("red")), List(SString("blue"), SString("red")))
//  }
}
