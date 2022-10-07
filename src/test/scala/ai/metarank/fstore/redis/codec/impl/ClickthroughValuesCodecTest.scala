package ai.metarank.fstore.redis.codec.impl

import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.{Clickthrough, ClickthroughValues, EventId, ItemValue, Timestamp}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ClickthroughValuesCodecTest extends AnyFlatSpec with Matchers with BinCodecTest {
  it should "roundtrip ctv" in {
    roundtrip(
      ClickthroughValuesCodec,
      ClickthroughValues(
        ct = Clickthrough(
          id = EventId("i1"),
          ts = Timestamp.now,
          user = UserId("u1"),
          session = Some(SessionId("foo")),
          items = List(ItemId("p1")),
          interactions = List(TypedInteraction(ItemId("p1"), "click"))
        ),
        values = List(ItemValue(ItemId("p1"), List(SingleValue(FeatureName("foo"), 1.0))))
      )
    )
  }
}
