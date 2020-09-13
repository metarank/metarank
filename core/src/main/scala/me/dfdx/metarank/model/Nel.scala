package me.dfdx.metarank.model

import cats.data.NonEmptyList

object Nel {
  def apply[T](value: T) = NonEmptyList.one(value)
}
