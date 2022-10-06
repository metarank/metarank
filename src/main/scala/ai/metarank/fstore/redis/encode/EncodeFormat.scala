package ai.metarank.fstore.redis.encode

import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import ai.metarank.model.{ClickthroughValues, EventId, FeatureValue, Key, Scalar}

case class EncodeFormat(
    key: KCodec[Key],
    timeValue: VCodec[TimeValue],
    eventId: KCodec[EventId],
    ctv: VCodec[ClickthroughValues],
    scalar: VCodec[Scalar]
)

object EncodeFormat {
  val json = new EncodeFormat(
    key = keyEncoder,
    timeValue = VCodec.json[TimeValue](FeatureValue.timeValueCodec),
    eventId = idEncoder,
    ctv = VCodec.json[ClickthroughValues](ClickthroughValues.ctvCodec),
    scalar = VCodec.json[Scalar](Scalar.scalarCodec)
  )

  val keyEncoder: KCodec[Key] = new KCodec[Key] {
    override def encode(prefix: String, value: Key): String = s"$prefix/${value.scope.asString}/${value.feature.value}"
  }

  val idEncoder: KCodec[EventId] = new KCodec[EventId] {
    override def encode(prefix: String, value: EventId): String = s"$prefix/${value.value}"
  }
}
