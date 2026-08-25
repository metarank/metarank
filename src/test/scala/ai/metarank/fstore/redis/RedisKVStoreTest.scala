package ai.metarank.fstore.redis

import ai.metarank.fstore.codec.values.StringVCodec
import ai.metarank.fstore.codec.StoreFormat.BinaryStoreFormat
import ai.metarank.fstore.codec.KCodec
import ai.metarank.model.{FeatureValue, Key, Timestamp}
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scalar.SString
import ai.metarank.model.Scope.GlobalScope
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration.*

class RedisKVStoreTest extends AnyFlatSpec with Matchers with RedisTest {
  val now = Timestamp.now
  lazy val kv =
    RedisKVStore[String, String](client, "x", _ => 90.days)(using KCodec.wrap(identity, identity), StringVCodec)

  it should "get empty" in {
    kv.get(List("a", "b")).unsafeRunSync() shouldBe Map.empty
  }

  it should "write-read" in {
    kv.put(Map("foo" -> "bar")).unsafeRunSync()
    kv.get(List("foo")).unsafeRunSync() shouldBe Map("foo" -> "bar")
  }

  it should "accept state" in {
    val fmt = BinaryStoreFormat
    val f   = RedisKVStore[Key, FeatureValue](client, "fv", _ => 90.days)(using fmt.key, fmt.featureValue)
    RedisKVStore.valueSink
      .sink(f, fs2.Stream(ScalarValue(Key(GlobalScope, FeatureName("a")), now, SString("foo"), 90.days)))
      .unsafeRunSync()
  }
}
