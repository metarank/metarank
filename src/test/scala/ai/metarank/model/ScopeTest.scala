package ai.metarank.model

import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Scope.FieldScope
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ScopeTest extends AnyFlatSpec with Matchers {
  it should "decode item field scope" in {
    Scope.fromString("field=foo:bar") shouldBe Right(FieldScope("foo", "bar"))
  }
}
