package ai.metarank.fstore.file

import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.file.client.SortedDB
import ai.metarank.fstore.transfer.StateSource
import ai.metarank.model.Feature.StatsEstimatorFeature
import ai.metarank.model.Feature.StatsEstimatorFeature.StatsEstimatorConfig
import ai.metarank.model.State.StatsEstimatorState
import ai.metarank.model.{Feature, FeatureValue, Key, Timestamp, Write}
import ai.metarank.util.SortedGroupBy
import cats.effect.IO
import fs2.Stream

import scala.util.Random

case class FileStatsEstimatorFeature(config: StatsEstimatorConfig, db: SortedDB[Float], format: StoreFormat)
    extends StatsEstimatorFeature {
  override def put(action: Write.PutStatSample): IO[Unit] = IO.whenA(Feature.shouldSample(config.sampleRate))(for {
    key <- IO(format.key.encodeNoPrefix(action.key) + "/" + Random.nextInt(config.poolSize))
    _   <- IO(db.put(key, action.value.toFloat))
  } yield {})

  override def computeValue(key: Key, ts: Timestamp): IO[Option[FeatureValue.NumStatsValue]] = for {
    kb     <- IO(format.key.encodeNoPrefix(key))
    values <- IO(db.firstN(kb, config.poolSize).toList)
    pool   <- IO(values.map(kv => kv._2.toDouble))
  } yield {
    if (pool.isEmpty) None else Some(fromPool(key, ts, pool))
  }
}

object FileStatsEstimatorFeature {
  case class NumSample(key: Key, num: Double)
  implicit val statsSource: StateSource[StatsEstimatorState, FileStatsEstimatorFeature] =
    new StateSource[StatsEstimatorState, FileStatsEstimatorFeature] {
      override def source(f: FileStatsEstimatorFeature): fs2.Stream[IO, StatsEstimatorState] =
        Stream
          .fromBlockingIterator[IO](f.db.all(), 128)
          .evalMap(kv => IO.fromEither(parse(f, kv)))
          .through(SortedGroupBy.groupBy[NumSample, Key](_.key))
          .evalMap {
            case Nil             => IO.raiseError(new Exception("cannot parse key"))
            case all @ head :: _ => IO.pure(StatsEstimatorState(head.key, all.map(_.num).toArray))
          }
    }

  def parse(f: FileStatsEstimatorFeature, kv: (String, Float)): Either[Throwable, NumSample] = {
    val pos = kv._1.lastIndexOf('/')
    if (pos < 0) {
      Left(new Exception("cannot parse key"))
    } else {
      val keyString = kv._1.substring(0, pos)
      f.format.key.decodeNoPrefix(keyString).map(k => NumSample(k, kv._2))
    }
  }
}
