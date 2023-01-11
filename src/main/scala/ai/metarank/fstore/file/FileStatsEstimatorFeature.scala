package ai.metarank.fstore.file

import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.file.client.FileClient
import ai.metarank.fstore.file.client.FileClient.{KeyVal, NumCodec}
import ai.metarank.fstore.transfer.StateSource
import ai.metarank.model.Feature.StatsEstimatorFeature
import ai.metarank.model.Feature.StatsEstimatorFeature.StatsEstimatorConfig
import ai.metarank.model.State.StatsEstimatorState
import ai.metarank.model.{Feature, FeatureValue, Key, Timestamp, Write}
import ai.metarank.util.SortedGroupBy
import cats.effect.IO
import com.google.common.primitives.Doubles
import fs2.Stream
import org.apache.commons.lang3.ArrayUtils

import scala.util.Random

case class FileStatsEstimatorFeature(config: StatsEstimatorConfig, db: FileClient, prefix: String, format: StoreFormat)
    extends StatsEstimatorFeature {
  override def put(action: Write.PutStatSample): IO[Unit] = IO.whenA(Feature.shouldSample(config.sampleRate))(for {
    key <- IO(format.key.encode(prefix, action.key) + "/" + Random.nextInt(config.poolSize))
    _   <- IO(db.put(key.getBytes(), action.value))
  } yield {})

  override def computeValue(key: Key, ts: Timestamp): IO[Option[FeatureValue.NumStatsValue]] = for {
    kb     <- IO(format.key.encode(prefix, key).getBytes)
    values <- IO(db.firstN(kb, config.poolSize).toList)
    pool   <- IO(values.map(kv => NumCodec.readDouble(kv.value)))
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
          .fromBlockingIterator[IO](f.db.firstN(s"${f.prefix}/${f.config.name.value}".getBytes(), Int.MaxValue), 128)
          .evalMap(kv => IO.fromEither(parse(f, kv)))
          .through(SortedGroupBy.groupBy[NumSample, Key](_.key))
          .evalMap {
            case Nil             => IO.raiseError(new Exception("cannot parse key"))
            case all @ head :: _ => IO.pure(StatsEstimatorState(head.key, all.map(_.num).toArray))
          }
    }

  def parse(f: FileStatsEstimatorFeature, kv: KeyVal): Either[Throwable, NumSample] = {
    val pos = ArrayUtils.lastIndexOf(kv.key, '/'.toByte)
    if (pos < 0) {
      Left(new Exception("cannot parse key"))
    } else {
      val keyString = new String(kv.key, 0, pos)
      f.format.key.decode(keyString).map(k => NumSample(k, NumCodec.readDouble(kv.value)))
    }
  }
}
