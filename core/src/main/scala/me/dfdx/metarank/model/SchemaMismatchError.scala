package me.dfdx.metarank.model

case class SchemaMismatchError(field: String, error: String)
    extends Throwable(s"field $field is not matching schema: $error")
