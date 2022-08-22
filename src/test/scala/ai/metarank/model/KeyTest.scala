package ai.metarank.model

import ai.metarank.model.Identifier.UserId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scope.UserScope
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class KeyTest extends AnyFlatSpec with Matchers {

  it should "decode keys" in {
    Key.fromString("user=123/foo") shouldBe Right(Key(UserScope(UserId("123")), FeatureName("foo")))
  }

  it should "fail on broken input" in {
    Key.fromString("user=123") shouldBe a[Left[_, _]]
  }

}
