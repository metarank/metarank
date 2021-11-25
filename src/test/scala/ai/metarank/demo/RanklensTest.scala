package ai.metarank.demo

import ai.metarank.demo.RanklensTest.{CTJoin, Clickthrough}
import ai.metarank.feature.{FeatureMapping, WordCountFeature}
import ai.metarank.model.{Event, FieldName, ItemId, UserId}
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, RankingEvent}
import ai.metarank.model.FeatureSchema.{NumberFeatureSchema, StringFeatureSchema, WordCountSchema}
import ai.metarank.model.FeatureScope.ItemScope
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
import scala.concurrent.duration._

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
    )
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

    val source = env.fromCollection(events)
    val impres = source
      .flatMap(e =>
        e match {
          case f: FeedbackEvent => Some(f)
          case _                => None
        }
      )
      .keyBy(e =>
        e match {
          case int: InteractionEvent => int.ranking
          case rank: RankingEvent    => rank.id
        }
      )
      .process(new ImpressionInjectFunction("examine", 30.minutes))

    val merged = source.union(impres)
    val writes = merged.flatMap(e => mapping.features.flatMap(_.writes(e)))

    val updates = Featury.process(writes, featurySchema, 10.seconds)

    val cts = env.fromCollection(ctsList).watermark(_.ranking.timestamp.ts)

    val joined =
      Featury
        .join[Clickthrough](updates, cts, CTJoin, featurySchema)
        .filter(_.features.nonEmpty)
        .executeAndCollect(1000)
    joined.size should be > 0
  }
}

object RanklensTest {
  case class Clickthrough(
      ranking: RankingEvent,
      clicks: List[InteractionEvent],
      features: List[FeatureValue] = Nil
  )
  case object CTJoin extends Join[Clickthrough] {
    override def by(left: Clickthrough): Key.Tenant = Tenant("default")

    override def tags(left: Clickthrough): List[Key.Tag] =
      left.ranking.items.map(id => Tag(Scope(ItemScope.value), id.id.value))

    override def join(left: Clickthrough, values: List[FeatureValue]): Clickthrough =
      left.copy(features = left.features ++ values)
  }
}
