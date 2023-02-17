package ai.metarank.fstore.file

import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.file.client.SortedDB
import ai.metarank.fstore.transfer.StateSource
import ai.metarank.model.Feature.PeriodicCounterFeature
import ai.metarank.model.Feature.PeriodicCounterFeature.PeriodicCounterConfig
import ai.metarank.model.FeatureValue.PeriodicCounterValue
import ai.metarank.model.State.PeriodicCounterState
import ai.metarank.model.{FeatureValue, Key, Timestamp, Write}
import ai.metarank.util.SortedGroupBy
import cats.effect.IO

import scala.annotation.tailrec
import fs2.Stream
case class FilePeriodicCounterFeature(
    config: PeriodicCounterConfig,
    db: SortedDB[Int],
    format: StoreFormat
) extends PeriodicCounterFeature {
  override def put(action: Write.PeriodicIncrement): IO[Unit] = for {
    kb    <- IO(format.key.encodeNoPrefix(action.key) + "/" + action.ts.toStartOfPeriod(config.period).ts.toString)
    value <- IO(db.get(kb))
    _     <- IO(db.put(kb, value.getOrElse(0) + 1))
  } yield {}

  override def computeValue(key: Key, ts: Timestamp): IO[Option[FeatureValue.PeriodicCounterValue]] = for {
    values  <- IO(db.lastN(format.key.encodeNoPrefix(key), config.periods.max + 1))
    decoded <- IO.fromEither(decode(values.toList))
  } yield {
    if (decoded.isEmpty) None else Some(PeriodicCounterValue(key, ts, fromMap(decoded)))
  }

  @tailrec private def decode(
      list: List[(String, Int)],
      acc: List[(Timestamp, Long)] = Nil
  ): Either[Throwable, Map[Timestamp, Long]] = list match {
    case Nil => Right(acc.toMap)
    case head :: tail =>
      decodeTime(head._1) match {
        case Left(err) => Left(err)
        case Right(ts) => decode(tail, acc :+ (Timestamp(ts), head._2))
      }
  }

  def decodeTime(b: String): Either[Throwable, Long] = {
    val pos = b.lastIndexOf('/')
    if (pos < 0) {
      Left(new Exception("cannot decode ts"))
    } else {
      val tss = b.substring(pos + 1)
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
          .fromBlockingIterator[IO](f.db.all(), 128)
          .evalMap(kv => IO.fromEither(decode(f, kv)))
          .through(SortedGroupBy.groupBy[KeyTimeCount, Key](_.key))
          .evalMap {
            case Nil => IO.raiseError(new Exception("cannot decode kv"))
            case all @ head :: _ =>
              IO.pure(PeriodicCounterState(head.key, all.map(ktc => ktc.ts -> ktc.cnt.toLong).toMap))
          }
    }

  def decode(f: FilePeriodicCounterFeature, kv: (String, Int)): Either[Throwable, KeyTimeCount] = {
    val pos = kv._1.lastIndexOf('/')
    if (pos < 0) {
      Left(new Exception("cannot parse key"))
    } else {
      val keyString  = kv._1.substring(0, pos)
      val timeString = kv._1.substring(pos + 1)
      f.format.key.decodeNoPrefix(keyString) match {
        case Left(err)  => Left(err)
        case Right(key) => Right(KeyTimeCount(key, Timestamp(timeString.toLong), kv._2))
      }
    }
  }
}
