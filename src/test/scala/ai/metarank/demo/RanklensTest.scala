package ai.metarank.demo

import ai.metarank.demo.RanklensTest.{CTJoin, Clickthrough}
import ai.metarank.feature.{FeatureMapping, WordCountFeature}
import ai.metarank.model.{Event, ItemId, UserId}
import ai.metarank.model.Event.{ImpressionEvent, InteractionEvent}
import ai.metarank.model.FeatureSchema.{NumberFeatureSchema, StringFeatureSchema, WordCountSchema}
import ai.metarank.model.FeatureSource.Item
import ai.metarank.util.FlinkTest
import better.files.Resource
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser._
import io.findify.featury.flink.{Featury, Join}
import io.findify.featury.model.Key.{Scope, Tag, Tenant}
import io.findify.featury.model.{FeatureValue, Key, Schema, Timestamp, Write}
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.scala._

import scala.concurrent.duration._
import java.util.zip.GZIPInputStream
import scala.io.Source

class RanklensTest extends AnyFlatSpec with Matchers with FlinkTest {
  val features = List(
    NumberFeatureSchema("popularity", "popularity", Item),
    NumberFeatureSchema("vote_avg", "vote_avg", Item),
    NumberFeatureSchema("vote_cnt", "vote_cnt", Item),
    NumberFeatureSchema("budget", "budget", Item),
    NumberFeatureSchema("release_date", "release_date", Item),
    WordCountSchema("title_length", "title", Item),
    StringFeatureSchema(
      "genre",
      "genres",
      Item,
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
  val mapping       = FeatureMapping.fromFeatureSchema(features)
  val featurySchema = Schema(mapping.features.flatMap(_.states))

  it should "accept events" in {
    env.setRuntimeMode(RuntimeExecutionMode.BATCH)

    val events = Source
      .fromInputStream(new GZIPInputStream(Resource.my.getAsStream("/ranklens/events.jsonl.gz")))
      .getLines()
      .map(line =>
        decode[Event](line) match {
          case Left(value)  => throw new IllegalArgumentException(s"illegal format: $value")
          case Right(value) => value
        }
      )
      .toList
    val impressions = events.collect { case i: ImpressionEvent => i }
    val clicks      = events.collect { case c: InteractionEvent => c }.groupBy(_.impression)
    val ctsList = for {
      imp   <- impressions
      click <- clicks.get(imp.id)
    } yield {
      Clickthrough(imp, click)
    }

    val writes = env
      .fromCollection(events.flatMap(e => mapping.features.flatMap(_.writes(e))))
      .assignTimestampsAndWatermarks(
        WatermarkStrategy
          .forMonotonousTimestamps()
          .withTimestampAssigner(new SerializableTimestampAssigner[Write] {
            override def extractTimestamp(element: Write, recordTimestamp: Long): Long = element.ts.ts
          })
      )
    val updates = Featury.process(writes, featurySchema, 10.seconds)

    val cts = env
      .fromCollection(ctsList)
      .assignTimestampsAndWatermarks(
        WatermarkStrategy
          .forMonotonousTimestamps()
          .withTimestampAssigner(new SerializableTimestampAssigner[Clickthrough] {
            override def extractTimestamp(element: Clickthrough, recordTimestamp: Long): Long =
              element.impression.timestamp.ts
          })
      )
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
      impression: ImpressionEvent,
      clicks: List[InteractionEvent],
      features: List[FeatureValue] = Nil
  )
  case object CTJoin extends Join[Clickthrough] {
    override def by(left: Clickthrough): Key.Tenant = Tenant("default")

    override def tags(left: Clickthrough): List[Key.Tag] =
      left.impression.items.map(id => Tag(Scope(Item.asString), id.id.value))

    override def join(left: Clickthrough, values: List[FeatureValue]): Clickthrough =
      left.copy(features = left.features ++ values)
  }
}
