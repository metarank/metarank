package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.config.BoosterConfig.XGBoostConfig
import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.feature.RandomFeature.RandomFeatureSchema
import ai.metarank.fstore.memory.{MemTrainStore, MemPersistence}
import ai.metarank.ml.rank.LambdaMARTRanker.{LambdaMARTConfig, LambdaMARTPredictor}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{EventId, Timestamp}
import ai.metarank.util.{TestFeatureMapping, TestInteractionEvent, TestRankingEvent}
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class ClickthroughJoinBufferTest extends AnyFlatSpec with Matchers {
  val models = Map(
    "random" -> LambdaMARTConfig(
      backend = XGBoostConfig(),
      features = NonEmptyList.of(FeatureName("rand")),
      weights = Map("click" -> 1)
    )
  )
  val features     = List(RandomFeatureSchema(FeatureName("rand")))
  lazy val mapping = FeatureMapping.fromFeatureSchema(features, models)
  lazy val state   = MemPersistence(mapping.schema)
  lazy val cs      = MemTrainStore()

  it should "join rankings and interactions" in {
    val buffer = ClickthroughJoinBuffer(conf = ClickthroughJoinConfig(), values = state.values, cs, mapping = mapping)
    val now    = Timestamp.now
    buffer.process(TestRankingEvent(List("p1")).copy(id = EventId("i1"), timestamp = now)).unsafeRunSync()
    buffer.process(TestInteractionEvent("p1", "i1").copy(timestamp = now.plus(1.second))).unsafeRunSync()
    buffer.flushAll().unsafeRunSync()
    val cts = cs.getall().compile.toList.unsafeRunSync()
    cts shouldNot be(empty)
  }

  it should "not emit cts on no click" in {
    val buffer = ClickthroughJoinBuffer(conf = ClickthroughJoinConfig(), values = state.values, cs, mapping = mapping)
    val now    = Timestamp.now
    buffer.process(TestRankingEvent(List("p1")).copy(id = EventId("i1"), timestamp = now)).unsafeRunSync()
    buffer.cache.invalidateAll()
    Thread.sleep(100)
    val cts = buffer.flushQueue().unsafeRunSync()
    cts shouldBe empty
  }

  it should "not fail when there are no ranking features" in {
    val mapping = TestFeatureMapping.empty()
    val state   = MemPersistence(mapping.schema)
    val cs      = MemTrainStore()
    val buffer  = ClickthroughJoinBuffer(conf = ClickthroughJoinConfig(), values = state.values, cs, mapping = mapping)
    val now     = Timestamp.now
    buffer.process(TestRankingEvent(List("p1")).copy(id = EventId("i1"), timestamp = now)).unsafeRunSync()
    buffer.flushAll().unsafeRunSync()
    val cts = cs.getall().compile.toList.unsafeRunSync()
    cts should be(empty)
  }
}
