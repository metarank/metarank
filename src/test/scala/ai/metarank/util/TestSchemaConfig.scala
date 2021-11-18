package ai.metarank.util

import ai.metarank.config.Config.SchemaConfig
import ai.metarank.model.FieldSchema.{BooleanFieldSchema, NumberFieldSchema, StringFieldSchema}

object TestSchemaConfig {
  def apply() = SchemaConfig(
    metadata = List(
      StringFieldSchema("title", required = true),
      NumberFieldSchema("avail", required = true)
    ),
    impression = List(
      StringFieldSchema("source", required = true),
      NumberFieldSchema("filters", required = true)
    ),
    interaction = List(
      BooleanFieldSchema("attr", required = false)
    )
  )
}
