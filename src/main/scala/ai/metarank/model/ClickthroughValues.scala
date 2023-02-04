package ai.metarank.model
import cats.data.NonEmptyList
import io.circe.Codec
import io.circe.generic.semiauto._

case class ClickthroughValues(ct: Clickthrough, values: List[ItemValue])

object ClickthroughValues {
  implicit val ctvJsonCodec: Codec[ClickthroughValues] = deriveCodec[ClickthroughValues]
}
