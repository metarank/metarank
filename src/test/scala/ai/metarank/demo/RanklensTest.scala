package ai.metarank.demo

import ai.metarank.feature.{FeatureMapping, WordCountFeature}
import ai.metarank.model.{Clickthrough, Event, FieldName, ItemId, UserId}
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, RankingEvent}
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope, TenantScope, UserScope}
import ai.metarank.model.FieldName.Metadata
import ai.metarank.util.{FlinkTest, ImpressionInjectFunction, RanklensEvents}
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.findify.featury.flink.{Featury, Join}
import io.findify.featury.model.Key.{Scope, Tag, Tenant}
import io.findify.featury.model.{FeatureValue, Key, Schema, Timestamp, Write}
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.scala._
import ai.metarank.util.DataStreamOps._
import org.apache.flink.api.scala.extensions._

import scala.concurrent.duration._
import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.model.Clickthrough.CTJoin
import org.apache.flink.streaming.api.scala.extensions.acceptPartialFunctions

class RanklensTest extends AnyFlatSpec with Matchers with FlinkTest {
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
    InteractedWithSchema("clicked_genre", "click", FieldName(Metadata, "genre"), SessionScope, Some(10), Some(24.hours))
  )

  it should "accept events" in {
    val mapping       = FeatureMapping.fromFeatureSchema(features)
    val featurySchema = Schema(mapping.features.flatMap(_.states))
    env.setRuntimeMode(RuntimeExecutionMode.BATCH)

    val events      = RanklensEvents()
    val impressions = events.collect { case i: RankingEvent => i }
    val clicks      = events.collect { case c: InteractionEvent => c }.groupBy(_.ranking)
    val ctsList = for {
      imp   <- impressions
      click <- clicks.get(imp.id)
    } yield {
      Clickthrough(imp, click)
    }

    val source = env.fromCollection(events).watermark(_.timestamp.ts)
    val impres = source
      .collect { case f: FeedbackEvent => f }
      .keyingBy {
        case int: InteractionEvent => int.ranking
        case rank: RankingEvent    => rank.id
      }
      .process(new ImpressionInjectFunction("impression", 30.minutes))

    val merged = source.union(impres)
    val writes = merged.flatMap(e => mapping.features.flatMap(_.writes(e)))

    val updates = Featury.process(writes, featurySchema, 10.seconds)

    val cts = env.fromCollection(ctsList).watermark(_.ranking.timestamp.ts)

    val joined =
      Featury
        .join[Clickthrough](updates, cts, CTJoin, featurySchema)
        //.filter(_.features.exists(_.key.name.value.startsWith("clicked_genre")))
        .executeAndCollect(1000)
    joined.size should be > 0
  }
}
