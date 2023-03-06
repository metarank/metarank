package ai.metarank.model

import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Scope.ItemFieldScope
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ScopeTest extends AnyFlatSpec with Matchers {
  it should "decode item field scope" in {
    Scope.fromString("item.foo=p1.bar") shouldBe Right(ItemFieldScope(ItemId("p1"), "foo", "bar"))
  }
}
