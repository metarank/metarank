package ai.metarank.model

import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Scope.{RankingFieldScope, ItemFieldScope}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ScopeTest extends AnyFlatSpec with Matchers {
  it should "decode field scope" in {
    Scope.fromString("field=foo:bar") shouldBe Right(ItemFieldScope("foo", "bar"))
  }

  it should "decode item+field scope" in {
    Scope.fromString("fi=foo:bar:i1") shouldBe Right(RankingFieldScope("foo", "bar", ItemId("i1")))
  }
}
