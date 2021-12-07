package ai.metarank.e2e

import ai.metarank.FeatureMapping
import ai.metarank.config.Config.InteractionConfig
import ai.metarank.feature.WordCountFeature
import ai.metarank.model.{Clickthrough, Event, FieldName, ItemId, UserId}
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, RankingEvent}
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope, TenantScope, UserScope}
import ai.metarank.model.FieldName.Metadata
import ai.metarank.util.{FlinkTest, RanklensEvents}
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.findify.featury.flink.{Featury, Join}
import org.apache.flink.api.common.RuntimeExecutionMode
import io.findify.flinkadt.api._
import ai.metarank.flow.DataStreamOps._

import scala.language.higherKinds
import scala.concurrent.duration._
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.flow.{ClickthroughJoin, ClickthroughJoinFunction, DatasetSink, ImpressionInjectFunction}
import better.files.File
import org.apache.flink.streaming.api.scala.extensions.acceptPartialFunctions

class RanklensTest extends AnyFlatSpec with Matchers with FlinkTest {
  import ai.metarank.mode.TypeInfos._
  val features = List(
    NumberFeatureSchema("popularity", FieldName(Metadata, "popularity"), ItemScope),
    NumberFeatureSchema("vote_avg", FieldName(Metadata, "vote_avg"), ItemScope),
    NumberFeatureSchema("vote_cnt", FieldName(Metadata, "vote_cnt"), ItemScope),
    NumberFeatureSchema("budget", FieldName(Metadata, "budget"), ItemScope),
    NumberFeatureSchema("release_date", FieldName(Metadata, "release_date"), ItemScope),
    WordCountSchema("title_length", FieldName(Metadata, "title"), ItemScope),
    StringFeatureSchema(
      "genre",
      FieldName(Metadata, "genres"),
      ItemScope,
      NonEmptyList.of(
        "drama",
        "comedy",
        "thriller",
        "action",
        "adventure",
        "romance",
        "crime",
        "science fiction",
        "fantasy",
        "family",
        "horror",
        "mystery",
        "animation",
        "history",
        "music"
      )
    ),
    RateFeatureSchema("ctr", "impression", "click", 24.hours, List(7, 30), ItemScope),
    InteractedWithSchema(
      "clicked_genre",
      "click",
      FieldName(Metadata, "genres"),
      SessionScope,
      Some(10),
      Some(24.hours)
    )
  )

  val inters = List(InteractionConfig("click", 1.0))

  it should "accept events" in {
    val mapping = FeatureMapping.fromFeatureSchema(features, inters)
    env.setRuntimeMode(RuntimeExecutionMode.BATCH)

    val events = RanklensEvents()

    val source = env.fromCollection(events).watermark(_.timestamp.ts)
    val groupedFeedback = source
      .collect { case f: FeedbackEvent => f }
      .keyingBy {
        case interaction: InteractionEvent => interaction.ranking
        case ranking: RankingEvent         => ranking.id
      }
    val impressions   = groupedFeedback.process(ImpressionInjectFunction("impression", 30.minutes))
    val clickthroughs = groupedFeedback.process(ClickthroughJoinFunction())
    val merged        = source.union(impressions)
    val writes        = merged.flatMap(e => mapping.features.flatMap(_.writes(e)))

    val updates = Featury.process(writes, mapping.schema, 10.seconds)

    val joined   = Featury.join[Clickthrough](updates, clickthroughs, ClickthroughJoin, mapping.schema)
    val computed = joined.map(ct => ct.copy(values = mapping.map(ct.ranking, ct.features, ct.interactions)))
    val dir      = File.newTemporaryDirectory("csv_")
    computed.sinkTo(DatasetSink(mapping, s"file://$dir"))
    env.execute()
    val br = 1
  }
}
