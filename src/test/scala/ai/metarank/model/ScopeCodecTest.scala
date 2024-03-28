package ai.metarank.model

import ai.metarank.fstore.codec.impl.ScopeCodec
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Scope.{ItemFieldScope, RankingFieldScope}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ScopeCodecTest extends AnyFlatSpec with Matchers {
  it should "decode field scope" in {
    ScopeCodec.decode("field=foo:bar") shouldBe Right(ItemFieldScope("foo", "bar"))
  }

  it should "decode field scope with semicolons" in {
    ScopeCodec.decode("field=name:cod:modern warfare") shouldBe Right(ItemFieldScope("name", "cod:modern warfare"))
  }

  it should "decode field scope with slashes" in {
    ScopeCodec.decode("field=name:a/v cable") shouldBe Right(ItemFieldScope("name", "a/v cable"))
  }

  it should "decode item+field scope" in {
    ScopeCodec.decode("irf=foo:bar:i1") shouldBe Right(RankingFieldScope("foo", "bar", ItemId("i1")))
  }

  it should "decode item+field scope with semicolons" in {
    ScopeCodec.decode("irf=query:cod:modern warfare:id1") shouldBe Right(
      RankingFieldScope("query", "cod:modern warfare", ItemId("id1"))
    )
  }
}
