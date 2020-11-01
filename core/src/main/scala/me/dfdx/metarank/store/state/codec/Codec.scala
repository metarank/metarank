package me.dfdx.metarank.store.state.codec

import java.io.{DataInput, DataOutput}

import magnolia.{CaseClass, Magnolia, SealedTrait}
import scala.language.experimental.macros

trait Codec[T] {
  def read(in: DataInput): T
  def write(value: T, out: DataOutput): Unit
}

object Codec {
  type Typeclass[T] = Codec[T]

  def combine[T](caseClass: CaseClass[Codec, T]): Codec[T] = new Codec[T] {
    override def read(in: DataInput): T = caseClass.construct(param => {
      param.typeclass.read(in)
    })

    override def write(value: T, out: DataOutput): Unit = caseClass.parameters.foreach(param => {
      param.typeclass.write(param.dereference(value), out)
    })
  }
  def dispatch[T](sealedTrait: SealedTrait[Codec, T]): Codec[T] = ???

  implicit def gen[T]: Codec[T] = macro Magnolia.gen[T]

  implicit val stringCodec = new Codec[String] {
    override def read(in: DataInput): String                 = in.readUTF()
    override def write(value: String, out: DataOutput): Unit = out.writeUTF(value)
  }

  implicit val intCodec = new Codec[Int] {
    override def read(in: DataInput): Int                 = in.readInt()
    override def write(value: Int, out: DataOutput): Unit = out.writeInt(value)
  }
}
