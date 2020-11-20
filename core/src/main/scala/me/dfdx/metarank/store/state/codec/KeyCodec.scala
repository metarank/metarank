package me.dfdx.metarank.store.state.codec

import java.io.{DataInput, DataOutput}

import magnolia.{CaseClass, Magnolia, SealedTrait}

import scala.language.experimental.macros

trait KeyCodec[T] {
  def write(value: T): String
}

object KeyCodec {
  type Typeclass[T] = KeyCodec[T]

  def combine[T](caseClass: CaseClass[KeyCodec, T]): KeyCodec[T] = new KeyCodec[T] {
    override def write(value: T): String = caseClass.parameters
      .map(param => {
        param.typeclass.write(param.dereference(value))
      })
      .mkString("/")
  }
  def dispatch[T](sealedTrait: SealedTrait[KeyCodec, T]): KeyCodec[T] = ???

  implicit def gen[T]: KeyCodec[T] = macro Magnolia.gen[T]

  implicit val stringKeyCodec = new KeyCodec[String] {
    override def write(value: String): String = value
  }

  implicit val intKeyCodec = new KeyCodec[Int] {
    override def write(value: Int): String = value.toString
  }

}
