package me.dfdx.metarank.store.state.codec

import java.io.{DataInput, DataOutput}

import cats.data.NonEmptyList
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
  def dispatch[T](sealedTrait: SealedTrait[Codec, T]): Codec[T] = new Codec[T] {
    override def read(in: DataInput): T = {
      val index = in.readInt()
      val codec = sealedTrait.subtypes(index)
      codec.typeclass.read(in)
    }

    override def write(value: T, out: DataOutput): Unit = {
      sealedTrait.dispatch(value)(subtype => {
        out.writeInt(subtype.index)
        subtype.typeclass.write(subtype.cast(value), out)
      })
    }
  }

  implicit def gen[T]: Codec[T] = macro Magnolia.gen[T]

  implicit val stringCodec = new Codec[String] {
    override def read(in: DataInput): String                 = in.readUTF()
    override def write(value: String, out: DataOutput): Unit = out.writeUTF(value)
  }

  implicit val intCodec = new Codec[Int] {
    override def read(in: DataInput): Int                 = in.readInt()
    override def write(value: Int, out: DataOutput): Unit = out.writeInt(value)
  }

  implicit val boolCodec = new Codec[Boolean] {
    override def read(in: DataInput): Boolean                 = in.readBoolean()
    override def write(value: Boolean, out: DataOutput): Unit = out.writeBoolean(value)
  }

  implicit val doubleCodec = new Codec[Double] {
    override def read(in: DataInput): Double                 = in.readDouble()
    override def write(value: Double, out: DataOutput): Unit = out.writeDouble(value)
  }

  implicit def nelCodec[T](implicit itemCodec: Codec[T]): Codec[NonEmptyList[T]] = new Codec[NonEmptyList[T]] {
    override def read(in: DataInput): NonEmptyList[T] = {
      val length = in.readInt()
      val buffer = for {
        _ <- 0 until length
      } yield {
        itemCodec.read(in)
      }
      NonEmptyList.fromListUnsafe(buffer.toList)
    }
    override def write(value: NonEmptyList[T], out: DataOutput): Unit = {
      out.writeInt(value.size)
      value.toList.foreach(itemCodec.write(_, out))
    }
  }
}
