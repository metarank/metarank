package ai.metarank.model
import io.circe.Codec
import io.circe.generic.semiauto._

case class ClickthroughValues(ct: Clickthrough, values: List[ItemValue])

object ClickthroughValues {
  implicit val ctvCodec: Codec[ClickthroughValues] = deriveCodec[ClickthroughValues]
}
