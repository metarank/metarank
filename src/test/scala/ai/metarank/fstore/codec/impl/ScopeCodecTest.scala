package ai.metarank.fstore.codec.impl

import ai.metarank.fstore.codec.impl.FeatureValueCodec.ScopeCodec
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Scope.ItemFieldScope
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

class ScopeCodecTest extends AnyFlatSpec with Matchers {
  it should "work with itemfield scopes" in {
    val buffer   = new ByteArrayOutputStream()
    val expected = ItemFieldScope("foo", "bar")
    ScopeCodec.write(expected, new DataOutputStream(buffer))
    val parsed = ScopeCodec.read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray)))
    parsed shouldBe expected
  }

}
