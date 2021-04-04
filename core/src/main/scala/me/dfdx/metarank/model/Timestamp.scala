package me.dfdx.metarank.model

import io.circe.{Codec, Decoder, Encoder}
import me.dfdx.metarank.model.Timestamp.MILLIS_IN_DAY

import scala.util.Try

/** A wrapper on number of milliseconds from 1970-01-01
  * @param value
  */
case class Timestamp(value: Long) extends AnyVal {
  def day: Int = math.round(value.toDouble / MILLIS_IN_DAY.toDouble).toInt
}

object Timestamp {
  val MILLIS_IN_DAY = 24 * 60 * 60 * 1000L
  implicit val tsCodec = Codec.from(
    decodeA = Decoder.decodeString
      .emapTry(str => Try(str.toLong))
      .map(Timestamp.apply)
      .ensure(_.value > 0L, "timestamp cannot be negative")
      .ensure(!_.value.toFloat.isNaN, "timestamp cannot be NaN"),
    encodeA = Encoder.instance[Timestamp](ts => Encoder.encodeString(ts.value.toString))
  )
  def day(n: Int) = new Timestamp(n * MILLIS_IN_DAY)
}
