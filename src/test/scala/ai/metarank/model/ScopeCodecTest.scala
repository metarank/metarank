package ai.metarank.model

import ai.metarank.fstore.codec.impl.ScopeCodec
import ai.metarank.model.Identifier.{ItemId, RankingId, SessionId, UserId}
import ai.metarank.model.Scope.{
  GlobalScope,
  ItemFieldScope,
  ItemScope,
  RankingFieldScope,
  RankingScope,
  SessionScope,
  UserScope
}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ScopeCodecTest extends AnyFlatSpec with Matchers {
  // these strings are the persisted Redis/file keyspace: they must never change
  it should "encode all scopes into stable key strings" in {
    ScopeCodec.encode(UserScope(UserId("u1"))) shouldBe "user=u1"
    ScopeCodec.encode(ItemScope(ItemId("p1"))) shouldBe "item=p1"
    ScopeCodec.encode(RankingScope(RankingId("r1"))) shouldBe "ranking=r1"
    ScopeCodec.encode(ItemFieldScope("color", "red")) shouldBe "field=color:red"
    ScopeCodec.encode(RankingFieldScope("q", "jeans", ItemId("p1"))) shouldBe "irf=q:jeans:p1"
    ScopeCodec.encode(GlobalScope) shouldBe "global"
    ScopeCodec.encode(SessionScope(SessionId("s1"))) shouldBe "session=s1"
  }

  it should "decode all scope key strings" in {
    ScopeCodec.decode("user=u1") shouldBe Right(UserScope(UserId("u1")))
    ScopeCodec.decode("item=p1") shouldBe Right(ItemScope(ItemId("p1")))
    ScopeCodec.decode("ranking=r1") shouldBe Right(RankingScope(RankingId("r1")))
    ScopeCodec.decode("global") shouldBe Right(GlobalScope)
    ScopeCodec.decode("session=s1") shouldBe Right(SessionScope(SessionId("s1")))
  }

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
