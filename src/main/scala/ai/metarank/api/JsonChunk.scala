package ai.metarank.api

import fs2.Chunk
import io.circe.{Encoder, Printer}
import io.circe.syntax._

object JsonChunk {
  val jsonFormat = Printer(
    dropNullValues = true,
    sortKeys = true,
    indent = "  "
  )

  def apply[T: Encoder](value: T): Chunk[Byte] = {
    Chunk.array(jsonFormat.print(value.asJson).getBytes())
  }
}
