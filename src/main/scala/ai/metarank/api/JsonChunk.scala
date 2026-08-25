package ai.metarank.api

import io.circe.{Encoder, Printer}
import io.circe.syntax.*
import scodec.bits.ByteVector

object JsonChunk {
  val jsonFormat = Printer(
    dropNullValues = true,
    sortKeys = true,
    indent = "  "
  )

  def apply[T: Encoder](value: T): ByteVector = {
    ByteVector.apply(jsonFormat.print(value.asJson).getBytes())
  }
}
