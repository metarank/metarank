package ai.metarank.e2e

import ai.metarank.FeatureMapping
import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.{Config, CoreConfig}
import ai.metarank.flow.ClickthroughJoinBuffer
import ai.metarank.fstore.memory.{MemClickthroughStore, MemPersistence}
import ai.metarank.main.command.train.SplitStrategy
import ai.metarank.main.command.{Import, Train}
import ai.metarank.model.Event.{InteractionEvent, ItemRelevancy, RankingEvent}
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.{EventId, Timestamp}
import ai.metarank.rank.{LambdaMARTModel, Ranker}
import ai.metarank.util.RanklensEvents
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import org.apache.commons.io.IOUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class RanklensTest extends AnyFlatSpec with Matchers {
  val config = Config
    .load(IOUtils.resourceToString("/ranklens/config.yml", StandardCharsets.UTF_8))
    .unsafeRunSync()
  val mapping     = FeatureMapping.fromFeatureSchema(config.features, config.models).optimize()
  lazy val file   = Files.createTempFile("events", ".jsonl")
  lazy val store  = MemPersistence(mapping.schema)
  lazy val cts    = MemClickthroughStore()
  val model       = mapping.models("xgboost").asInstanceOf[LambdaMARTModel]
  val modelConfig = config.models("xgboost").asInstanceOf[LambdaMARTConfig]
  lazy val buffer = ClickthroughJoinBuffer(ClickthroughJoinConfig(), store.values, cts, mapping)

  it should "import events" in {
    Import.slurp(fs2.Stream.emits(RanklensEvents()), store, mapping, buffer).unsafeRunSync()
    buffer.flushQueue(Timestamp.max).unsafeRunSync()
  }

  it should "train the xgboost model" in {
    Train.train(store, cts, model, "xgboost", modelConfig.backend, SplitStrategy.default).unsafeRunSync()
  }

  it should "rerank things" in {
    val ranking = RankingEvent(
      id = EventId("event1"),
      timestamp = Timestamp(1636993838000L),
      user = Some(UserId("u1")),
      session = Some(SessionId("s1")),
      items = NonEmptyList.of(
        ItemRelevancy(ItemId("96610"), 0.0),
        ItemRelevancy(ItemId("8371"), 0.0),
        ItemRelevancy(ItemId("4975"), 0.0),
        ItemRelevancy(ItemId("7163"), 0.0),
        ItemRelevancy(ItemId("111759"), 0.0),
        ItemRelevancy(ItemId("102880"), 0.0),
        ItemRelevancy(ItemId("109487"), 0.0),
        ItemRelevancy(ItemId("95309"), 0.0),
        ItemRelevancy(ItemId("115713"), 0.0),
        ItemRelevancy(ItemId("122882"), 0.0),
        ItemRelevancy(ItemId("134130"), 0.0),
        ItemRelevancy(ItemId("8644"), 0.0),
        ItemRelevancy(ItemId("49278"), 0.0),
        ItemRelevancy(ItemId("2916"), 0.0),
        ItemRelevancy(ItemId("2012"), 0.0),
        ItemRelevancy(ItemId("68358"), 0.0),
        ItemRelevancy(ItemId("132046"), 0.0),
        ItemRelevancy(ItemId("2709"), 0.0),
        ItemRelevancy(ItemId("79357"), 0.0),
        ItemRelevancy(ItemId("5903"), 0.0),
        ItemRelevancy(ItemId("107406"), 0.0),
        ItemRelevancy(ItemId("1210"), 0.0),
        ItemRelevancy(ItemId("85056"), 0.0),
        ItemRelevancy(ItemId("1270"), 0.0)
      )
    )
    val i1 = InteractionEvent(
      id = EventId("event2"),
      item = ItemId("102880"),
      timestamp = Timestamp(1636993838000L),
      user = Some(UserId("u1")),
      session = Some(SessionId("s1")),
      `type` = "click",
      ranking = Some(EventId("event1"))
    )
    val i2 = i1.copy(item = ItemId("109487"))
    val i3 = i1.copy(item = ItemId("8644"))

    val ranker = Ranker(mapping, store)
    val resp1  = ranker.rerank(ranking, "xgboost", true).unsafeRunSync()

    Import.slurp(fs2.Stream.emits(List(ranking, i1, i2, i3)), store, mapping, buffer).unsafeRunSync()
    val resp2 = ranker.rerank(ranking, "xgboost", true).unsafeRunSync()
    resp1 shouldNot be(resp2)
    resp1.items.map(_.item.value) shouldNot be(resp2.items.map(_.item.value))
  }
}
