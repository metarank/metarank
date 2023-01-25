package ai.metarank.fstore.codec.impl

import ai.metarank.fstore.codec.impl.ClickthroughValuesCodec.MValueCodec
import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.Field.StringField
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.{CategoryValue, SingleValue, VectorValue}
import ai.metarank.model.{Clickthrough, ClickthroughValues, EventId, ItemValue, Timestamp}
import better.files.File
import org.apache.commons.io.IOUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}
import java.util.UUID

class ClickthroughValuesCodecTest extends AnyFlatSpec with Matchers {
  val ctv = ClickthroughValues(
    ct = Clickthrough(
      id = EventId("e1"),
      ts = Timestamp.date(2022, 11, 17, 15, 32, 0),
      user = Some(UserId("alice")),
      session = Some(SessionId("wow")),
      items = List(ItemId("p1"), ItemId("p2"), ItemId("p3"), ItemId("p4")),
      interactions = List(TypedInteraction(ItemId("p2"), "click"), TypedInteraction(ItemId("p2"), "purchase")),
      rankingFields = List(StringField("foo", "bar"))
    ),
    values = List(
      ItemValue(
        ItemId("p1"),
        List(
          SingleValue(FeatureName("f1"), 1.0),
          VectorValue(FeatureName("f2"), Array(1.0), 1),
          CategoryValue(FeatureName("f3"), "x", 0)
        )
      ),
      ItemValue(
        ItemId("p2"),
        List(
          SingleValue(FeatureName("f1"), 1.0),
          VectorValue(FeatureName("f2"), Array(1.0), 1),
          CategoryValue(FeatureName("f3"), "x", 0)
        )
      ),
      ItemValue(
        ItemId("p3"),
        List(
          SingleValue(FeatureName("f1"), 1.0),
          VectorValue(FeatureName("f2"), Array(1.0), 1),
          CategoryValue(FeatureName("f3"), "x", 0)
        )
      )
    )
  )

  lazy val bytes = IOUtils.resourceToByteArray("/codec/ctv.bin")

  it should "roundtrip ctv" in {
    val out = new ByteArrayOutputStream()
    ClickthroughValuesCodec.write(ctv, new DataOutputStream(out))
    // val temp    = File("/tmp/ctv.bin").writeByteArray(out.toByteArray)
    val decoded = ClickthroughValuesCodec.read(new DataInputStream(new ByteArrayInputStream(out.toByteArray)))
    decoded shouldBe ctv
  }

  it should "match the reference bytes" in {
    val out = new ByteArrayOutputStream()
    ClickthroughValuesCodec.write(ctv, new DataOutputStream(out))
    val actual = out.toByteArray
    actual should contain theSameElementsInOrderAs (bytes)
  }

  it should "decode reference bytes" in {
    val actual = ClickthroughValuesCodec.read(new DataInputStream(new ByteArrayInputStream(bytes)))
    actual shouldBe ctv
  }

  it should "handle NaNs" in {
    val out = new ByteArrayOutputStream()
    MValueCodec.write(SingleValue(FeatureName("foo"), Double.NaN), new DataOutputStream(out))
    val decoded = MValueCodec.read(new DataInputStream(new ByteArrayInputStream(out.toByteArray)))
    decoded shouldBe SingleValue(FeatureName("foo"), Double.NaN)
  }
}
