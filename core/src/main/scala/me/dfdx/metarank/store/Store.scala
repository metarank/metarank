package me.dfdx.metarank.store

trait Store {}

object Store {
  trait Key[T] {
    def string: String
  }
}
