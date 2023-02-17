package ai.metarank.fstore.file

import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.file.client.{FileClient, SortedDB}
import ai.metarank.fstore.transfer.StateSource
import ai.metarank.model.Feature.MapFeature
import ai.metarank.model.Feature.MapFeature.MapConfig
import ai.metarank.model.FeatureValue.MapValue
import ai.metarank.model.State.MapState
import ai.metarank.model.{FeatureValue, Key, Scalar, Timestamp, Write}
import ai.metarank.util.SortedGroupBy
import cats.effect.IO
import org.apache.commons.lang3.ArrayUtils
import fs2.Stream

import scala.annotation.tailrec

case class FileMapFeature(config: MapConfig, db: SortedDB[Array[Byte]], format: StoreFormat) extends MapFeature {
  override def put(action: Write.PutTuple): IO[Unit] = IO {
    val key = format.key.encodeNoPrefix(action.key) + "/" + action.mapKey
    action.value match {
      case None    => db.del(key)
      case Some(s) => db.put(key, format.scalar.encode(s))
    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[FeatureValue.MapValue]] = for {
    kb      <- IO(format.key.encodeNoPrefix(key))
    values  <- IO(db.firstN(kb, Int.MaxValue))
    decoded <- IO.fromEither(decode(values.toList))
  } yield {
    if (decoded.isEmpty) None else Some(MapValue(key, ts, decoded))
  }

  @tailrec private def decode(
      list: List[(String, Array[Byte])],
      acc: List[(String, Scalar)] = Nil
  ): Either[Throwable, Map[String, Scalar]] =
    list match {
      case Nil => Right(acc.toMap)
      case head :: tail =>
        decodeKey(head._1) match {
          case Left(err) => Left(err)
          case Right(mapKey) =>
            format.scalar.decode(head._2) match {
              case Left(err) => Left(err)
              case Right(s)  => decode(tail, acc :+ (mapKey -> s))
            }
        }
    }

  def decodeKey(b: String): Either[Throwable, String] = {
    val sep = b.lastIndexOf('/')
    if (sep < 0) {
      Left(new Exception("separator not found"))
    } else {
      Right(b.substring(sep + 1))
    }
  }

}

object FileMapFeature {
  case class KKV(key: Key, mapKey: String, value: Scalar)
  implicit val mapStateSource: StateSource[MapState, FileMapFeature] = new StateSource[MapState, FileMapFeature] {
    override def source(f: FileMapFeature): fs2.Stream[IO, MapState] =
      Stream
        .fromBlockingIterator[IO](f.db.all(), 128)
        .evalMap(kv => IO.fromEither(parse(f, kv)))
        .through(SortedGroupBy.groupBy[KKV, Key](_.key))
        .evalMap {
          case Nil             => IO.raiseError(new Exception("oops"))
          case all @ head :: _ => IO.pure(MapState(head.key, all.map(kkv => kkv.mapKey -> kkv.value).toMap))
        }

  }

  def parse(f: FileMapFeature, kv: (String, Array[Byte])): Either[Throwable, KKV] = {
    val sep = kv._1.lastIndexOf('/')
    if (sep < 0) {
      Left(new Exception("separator not found"))
    } else {
      val keyString = kv._1.substring(0, sep)
      val mapKey    = kv._1.substring(sep + 1)
      for {
        key   <- f.format.key.decodeNoPrefix(keyString)
        value <- f.format.scalar.decode(kv._2)
      } yield {
        KKV(key, mapKey, value)
      }
    }
  }

}
