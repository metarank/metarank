package me.dfdx.metarank.store.state.codec
import java.io.ByteArrayOutputStream

import magnolia._

import scala.language.experimental.macros

package object semiauto {
//  type Typeclass[T] = Codec[T]
//  def combine[T](caseClass: CaseClass[Codec, T]): Codec[T] = new Codec[T] {
//    override def read(in: Array[Byte]): T = {}
//
//    override def write(value: T): Array[Byte] = {
//      val buffer = new ByteArrayOutputStream()
//      caseClass.parameters.foreach(param => {
//        val blob = param.typeclass.write(param.dereference(value))
//        buffer.write(blob)
//      })
//      buffer.toByteArray
//    }
//  }
}
