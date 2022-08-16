package ai.metaranke2e.e2e

import ai.metarank.FeatureMapping
import ai.metarank.config.InputConfig.{SourceOffset, conf}
import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.{Config, Hostname, Port, SourceFormat}
import ai.metarank.config.StateStoreConfig.RedisStateConfig
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.fstore.redis.{RedisPersistence, RedisTest}
import ai.metarank.main.CliArgs.ImportArgs
import ai.metarank.main.api.RankApi
import ai.metarank.main.command.{Import, Train}
import ai.metarank.model.Event.{InteractionEvent, ItemRelevancy, RankingEvent}
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.{EventId, Timestamp}
import ai.metarank.rank.LambdaMARTModel
import ai.metarank.source.ModelCache
import ai.metarank.source.ModelCache.MemoryModelCache
import ai.metarank.source.format.JsonFormat
import ai.metarank.util.RanklensEvents
import better.files.File
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.apache.commons.io.IOUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.util.Random
import scala.concurrent.duration._
import io.circe.syntax._

import java.io.FileOutputStream

class RanklensTest extends AnyFlatSpec with Matchers {
  val config = Config
    .load(IOUtils.resourceToString("/ranklens/config.yml", StandardCharsets.UTF_8))
    .unsafeRunSync()
  val mapping     = FeatureMapping.fromFeatureSchema(config.features, config.models)
  lazy val file   = Files.createTempFile("events", ".jsonl")
  lazy val store  = MemPersistence(mapping.schema)
  val model       = mapping.models("xgboost").asInstanceOf[LambdaMARTModel]
  val modelConfig = config.models("xgboost").asInstanceOf[LambdaMARTConfig]

  it should "write events file" in {
    val stream = new FileOutputStream(file.toFile)
    IOUtils.write(RanklensEvents().map(_.asJson.noSpaces).mkString("\n"), stream, StandardCharsets.UTF_8)
    stream.close()
  }

  it should "import events" in {
    Import.slurp(store, mapping, ImportArgs(file, file, SourceOffset.Earliest, JsonFormat)).unsafeRunSync()
  }

  it should "train the xgboost model" in {
    Train.train(store, model, "xgboost", modelConfig.backend).unsafeRunSync()
  }

  it should "rerank things" in {
    val ranking = RankingEvent(
      id = EventId("event1"),
      timestamp = Timestamp(1636993838000L),
      user = UserId("u1"),
      session = Some(SessionId("s1")),
      items = NonEmptyList.of(
        ItemRelevancy(ItemId("7346"), 0.0),
        ItemRelevancy(ItemId("1971"), 0.0),
        ItemRelevancy(ItemId("69844"), 0.0),
        ItemRelevancy(ItemId("1246"), 0.0),
        ItemRelevancy(ItemId("3243"), 0.0),
        ItemRelevancy(ItemId("1644"), 0.0),
        ItemRelevancy(ItemId("6593"), 0.0),
        ItemRelevancy(ItemId("2599"), 0.0),
        ItemRelevancy(ItemId("3916"), 0.0)
      )
    )
    val interaction = InteractionEvent(
      id = EventId("event2"),
      item = ItemId("1246"),
      timestamp = Timestamp(1636993838000L),
      user = UserId("u1"),
      session = Some(SessionId("s1")),
      `type` = "click",
      ranking = Some(EventId("event1"))
    )

    val ranker = RankApi(mapping, store, MemoryModelCache(store))
    val resp1  = ranker.rerank(mapping, ranking, "xgboost", true).unsafeRunSync()
    val br     = 1

    Import.slurp(fs2.Stream.emits(List(ranking, interaction)), store, mapping).unsafeRunSync()
    val resp2 = ranker.rerank(mapping, ranking, "xgboost", true).unsafeRunSync()
    resp1 shouldNot be(resp2)
  }
}
