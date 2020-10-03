package me.dfdx.metarank.model

import cats.data.NonEmptyList

import scala.annotation.tailrec

object Nel {
  def apply[T](value: T) = NonEmptyList.one(value)

}
