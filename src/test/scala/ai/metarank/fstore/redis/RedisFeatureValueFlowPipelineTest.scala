package ai.metarank.fstore.redis

import ai.metarank.config.StateStoreConfig.RedisStateConfig.{CacheConfig, PipelineConfig}
import ai.metarank.flow.FeatureValueFlow
import ai.metarank.fstore.codec.StoreFormat.JsonStoreFormat
import ai.metarank.model.Field.NumberField
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scalar.SDouble
import ai.metarank.model.Scope.ItemScope
import ai.metarank.util.{TestFeatureMapping, TestItemEvent}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import fs2.Stream

import scala.concurrent.duration._

class RedisFeatureValueFlowPipelineTest extends AnyFlatSpec with Matchers with RedisTest {
  // a write batch smaller than maxSize stays in the pipeline buffer, so computeValue
  // sees it only through the syncState barrier
  override def pipeline = PipelineConfig(128, 10.minutes)

  it should "compute values for writes still buffered in the pipeline" in {
    val mapping = TestFeatureMapping()
    val store   = RedisPersistence(mapping.schema, client, client, client, CacheConfig(0, 0.seconds), JsonStoreFormat)
    val flow    = FeatureValueFlow(mapping, store)
    val event   = TestItemEvent("p1").copy(fields = List(NumberField("price", 10)))
    val values  = Stream.emit(event).through(flow.process).compile.toList.unsafeRunSync().flatten
    values shouldBe List(
      ScalarValue(Key(ItemScope(ItemId("p1")), FeatureName("price")), event.timestamp, SDouble(10.0), 90.days)
    )
  }
}
