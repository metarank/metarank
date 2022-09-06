package ai.metarank.model

import io.circe.{Codec, Decoder, Encoder}

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

case class Timestamp(ts: Long) {
  def isBefore(right: Timestamp)         = ts < right.ts
  def isBeforeOrEquals(right: Timestamp) = ts <= right.ts
  def isAfter(right: Timestamp)          = ts > right.ts
  def isAfterOrEquals(right: Timestamp)  = ts >= right.ts
  def plus(d: FiniteDuration)            = Timestamp(ts + d.toMillis)
  def minus(d: FiniteDuration)           = Timestamp(ts - d.toMillis)
  def toStartOfPeriod(period: FiniteDuration) = {
    val p = math.floor(ts.toDouble / period.toMillis).toLong
    Timestamp(p * period.toMillis)
  }
  def diff(other: Timestamp): FiniteDuration = {
    FiniteDuration(math.abs(other.ts - ts), TimeUnit.MILLISECONDS)
  }

  override def toString: String = Instant.ofEpochMilli(ts).atOffset(ZoneOffset.UTC).format(Timestamp.format)
}

object Timestamp {
  def now = new Timestamp(System.currentTimeMillis())
  def max = new Timestamp(Long.MaxValue)
  def date(year: Int, month: Int, day: Int, hour: Int, min: Int, sec: Int) =
    new Timestamp(LocalDateTime.of(year, month, day, hour, min, sec).toInstant(ZoneOffset.UTC).toEpochMilli)
  val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  implicit val timestampJson: Codec[Timestamp] = Codec.from(
    decodeA = Decoder.decodeLong.map(Timestamp.apply),
    encodeA = Encoder.encodeLong.contramap(_.ts)
  )

}
