package ai.metarank.fstore.codec.impl

import ai.metarank.model.Identifier.{ItemId, UserId}
import ai.metarank.model.Key
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scope.{ItemFieldScope, ItemScope, RankingFieldScope, UserScope}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class KeyCodecTest extends AnyFlatSpec with Matchers {
  it should "encode item keys" in {
    val encoded = KeyCodec.encode("prefix", Key(ItemScope(ItemId("id1")), FeatureName("fname")))
    encoded shouldBe "prefix/fname/item=id1"
  }

  it should "encode item-field keys" in {
    val encoded = KeyCodec.encode("prefix", Key(ItemFieldScope("category", "a/v cables"), FeatureName("fname")))
    encoded shouldBe "prefix/fname/field=category:a/v cables"
  }

  it should "encode user keys" in {
    val encoded = KeyCodec.encode("prefix", Key(UserScope(UserId("id1")), FeatureName("fname")))
    encoded shouldBe "prefix/fname/user=id1"
  }

  it should "roundtrip regular keys" in {
    roundtrip(Key(ItemScope(ItemId("i1")), FeatureName("price")))
  }

  it should "roundtrip field with slash scopes" in {
    roundtrip(Key(ItemFieldScope("class", "a/v cables"), FeatureName("price")))
  }

  def roundtrip(key: Key) = {
    roundtripPrefix("foo", key)
    roundtripNoPrefix(key)
  }

  def roundtripPrefix(prefix: String, key: Key) = {
    val encoded = KeyCodec.encode(prefix, key)
    val decoded = KeyCodec.decode(encoded)
    decoded shouldBe Right(key)
  }

  def roundtripNoPrefix(key: Key) = {
    val encoded = KeyCodec.encodeNoPrefix(key)
    val decoded = KeyCodec.decodeNoPrefix(encoded)
    decoded shouldBe Right(key)
  }
}
