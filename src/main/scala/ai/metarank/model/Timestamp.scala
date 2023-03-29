package ai.metarank.model

import io.circe.{Codec, Decoder, DecodingFailure, Encoder}

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

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

  object StringMillisTimestamp {
    val millisPattern      = "([0-9]{12,13})".r
    val unixSecondsPattern = "([0-9]{9,10})".r
    def unapply(in: String): Option[Timestamp] = in match {
      case millisPattern(millis)       => millis.toLongOption.map(Timestamp.apply)
      case unixSecondsPattern(seconds) => seconds.toLongOption.map(s => Timestamp(s * 1000L))
      case _                           => None
    }
  }

  object ISOTimestamp {
    val pattern = DateTimeFormatter.ISO_DATE_TIME
    def unapply(in: String): Option[Timestamp] = Try(LocalDateTime.parse(in, format)) match {
      case Success(ts) => Some(Timestamp(ts.toInstant(ZoneOffset.UTC).toEpochMilli))
      case Failure(ex) => None
    }
  }
  val MAX_UNIXTIME = 2000000000L
  val MIN_MILLIS   = 1000000000000L

  implicit val timestampEncoder: Encoder[Timestamp] = Encoder.encodeLong.contramap(_.ts)
  implicit val timestampDecoder: Decoder[Timestamp] = Decoder.instance(c =>
    c.as[String] match {
      case Left(_) =>
        c.as[Long] match {
          case Left(value) => Left(DecodingFailure(s"cannot decode timestamp: ${value}", c.history))
          case Right(seconds) if seconds < MAX_UNIXTIME => Right(Timestamp(seconds * 1000L))
          case Right(millis) if millis > MIN_MILLIS     => Right(Timestamp(millis))
          case Right(other) =>
            Left(DecodingFailure(s"cannot decode timestamp of $other, should be millis from epoch start", c.history))
        }
      case Right(StringMillisTimestamp(ts)) => Right(ts)
      case Right(ISOTimestamp(ts))          => Right(ts)
      case Right(other)                     => Left(DecodingFailure(s"cannot decode $other as a timestamp", c.history))
    }
  )

  implicit val timestampJsonCodec: Codec[Timestamp] = Codec.from(timestampDecoder, timestampEncoder)

}
