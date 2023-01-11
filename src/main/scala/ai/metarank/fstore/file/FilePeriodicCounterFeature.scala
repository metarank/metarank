package ai.metarank.fstore.file

import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.file.client.FileClient
import ai.metarank.fstore.file.client.FileClient.{KeyVal, NumCodec}
import ai.metarank.fstore.transfer.StateSource
import ai.metarank.model.Feature.PeriodicCounterFeature
import ai.metarank.model.Feature.PeriodicCounterFeature.PeriodicCounterConfig
import ai.metarank.model.FeatureValue.PeriodicCounterValue
import ai.metarank.model.State.PeriodicCounterState
import ai.metarank.model.{FeatureValue, Key, Timestamp, Write}
import ai.metarank.util.SortedGroupBy
import cats.effect.IO
import com.google.common.primitives.Ints
import org.apache.commons.lang3.ArrayUtils

import scala.annotation.tailrec
import fs2.Stream
case class FilePeriodicCounterFeature(
    config: PeriodicCounterConfig,
    db: FileClient,
    prefix: String,
    format: StoreFormat
) extends PeriodicCounterFeature {
  override def put(action: Write.PeriodicIncrement): IO[Unit] = for {
    kb <- IO(format.key.encode(prefix, action.key) + "/" + action.ts.toStartOfPeriod(config.period).ts.toString)
    _  <- IO(db.inc(kb.getBytes(), action.inc))
  } yield {}

  override def computeValue(key: Key, ts: Timestamp): IO[Option[FeatureValue.PeriodicCounterValue]] = for {
    values  <- IO(db.lastN(format.key.encode(prefix, key).getBytes, config.periods.max + 1))
    decoded <- IO.fromEither(decode(values.toList))
  } yield {
    val x = values
    if (decoded.isEmpty) None else Some(PeriodicCounterValue(key, ts, fromMap(decoded)))
  }

  @tailrec private def decode(
      list: List[KeyVal],
      acc: List[(Timestamp, Long)] = Nil
  ): Either[Throwable, Map[Timestamp, Long]] = list match {
    case Nil => Right(acc.toMap)
    case head :: tail =>
      decodeTime(head.key) match {
        case Left(err) => Left(err)
        case Right(ts) => decode(tail, acc :+ (Timestamp(ts), NumCodec.readInt(head.value)))
      }
  }

  def decodeTime(b: Array[Byte]): Either[Throwable, Long] = {
    val pos = ArrayUtils.lastIndexOf(b, '/'.toByte)
    if (pos < 0) {
      Left(new Exception("cannot decode ts"))
    } else {
      val tss = new String(b, pos + 1, b.length - pos - 1)
      tss.toLongOption match {
        case Some(ts) => Right(ts)
        case None     => Left(new Exception("cannot decode ts"))
      }
    }
  }
}

object FilePeriodicCounterFeature {
  case class KeyTimeCount(key: Key, ts: Timestamp, cnt: Int)
  implicit val pcState: StateSource[PeriodicCounterState, FilePeriodicCounterFeature] =
    new StateSource[PeriodicCounterState, FilePeriodicCounterFeature] {
      override def source(f: FilePeriodicCounterFeature): fs2.Stream[IO, PeriodicCounterState] =
        Stream
          .fromBlockingIterator[IO](f.db.firstN(s"${f.prefix}/${f.config.name.value}".getBytes(), Int.MaxValue), 128)
          .evalMap(kv => IO.fromEither(decode(f, kv)))
          .through(SortedGroupBy.groupBy[KeyTimeCount, Key](_.key))
          .evalMap {
            case Nil => IO.raiseError(new Exception("cannot decode kv"))
            case all @ head :: _ =>
              IO.pure(PeriodicCounterState(head.key, all.map(ktc => ktc.ts -> ktc.cnt.toLong).toMap))
          }
    }

  def decode(f: FilePeriodicCounterFeature, kv: KeyVal): Either[Throwable, KeyTimeCount] = {
    val pos = ArrayUtils.lastIndexOf(kv.key, '/'.toByte)
    if (pos < 0) {
      Left(new Exception("cannot parse key"))
    } else {
      val keyString  = new String(kv.key, 0, pos)
      val timeString = new String(kv.key, pos + 1, kv.key.length - pos - 1)
      f.format.key.decode(keyString) match {
        case Left(err)  => Left(err)
        case Right(key) => Right(KeyTimeCount(key, Timestamp(timeString.toLong), NumCodec.readInt(kv.value)))
      }
    }
  }
}
