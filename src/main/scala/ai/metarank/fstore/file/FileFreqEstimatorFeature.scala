package ai.metarank.fstore.file

import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.file.client.FileClient
import ai.metarank.fstore.file.client.FileClient.KeyVal
import ai.metarank.fstore.transfer.StateSource
import ai.metarank.model.Feature.{FreqEstimatorFeature, shouldSample}
import ai.metarank.model.Feature.FreqEstimatorFeature.FreqEstimatorConfig
import ai.metarank.model.FeatureValue.FrequencyValue
import ai.metarank.model.State.FreqEstimatorState
import ai.metarank.model.{FeatureValue, Key, Timestamp, Write}
import ai.metarank.util.SortedGroupBy
import cats.effect.IO
import fs2.Stream
import org.apache.commons.lang3.ArrayUtils

import scala.util.Random

case class FileFreqEstimatorFeature(config: FreqEstimatorConfig, db: FileClient, prefix: String, format: StoreFormat)
    extends FreqEstimatorFeature {
  override def put(action: Write.PutFreqSample): IO[Unit] =
    IO.whenA(shouldSample(config.sampleRate))(for {
      key <- IO(format.key.encode(prefix, action.key) + "/" + Random.nextInt(config.poolSize))
      _   <- IO(db.put(key.getBytes(), action.value.getBytes))
    } yield {})

  override def computeValue(key: Key, ts: Timestamp): IO[Option[FeatureValue.FrequencyValue]] = for {
    kb      <- IO(format.key.encode(prefix, key).getBytes)
    values  <- IO(db.firstN(kb, config.poolSize))
    samples <- IO(values.map(kv => new String(kv.value)).toList)
  } yield {
    freqFromSamples(samples).map(freq => FrequencyValue(key, ts, freq))
  }
}

object FileFreqEstimatorFeature {
  case class FreqSample(key: Key, s: String)
  implicit val fileFreqSource: StateSource[FreqEstimatorState, FileFreqEstimatorFeature] =
    new StateSource[FreqEstimatorState, FileFreqEstimatorFeature] {
      override def source(f: FileFreqEstimatorFeature): Stream[IO, FreqEstimatorState] = {
        Stream
          .fromBlockingIterator[IO](f.db.firstN(s"${f.prefix}/${f.config.name.value}".getBytes(), Int.MaxValue), 128)
          .evalMap(kv => IO.fromEither(decode(f, kv)))
          .through(SortedGroupBy.groupBy[FreqSample, Key](_.key))
          .evalMap {
            case all @ head :: _ => IO.pure(FreqEstimatorState(head.key, all.map(_.s)))
            case Nil             => IO.raiseError(new Exception("empty batch"))
          }
      }
    }

  def decode(f: FileFreqEstimatorFeature, kv: KeyVal): Either[Throwable, FreqSample] = {
    val pos = ArrayUtils.lastIndexOf(kv.key, '/'.toByte)
    if (pos < 0) {
      Left(new Exception("cannot parse kv"))
    } else {
      val keyString = new String(kv.key, 0, pos)
      f.format.key.decode(keyString) match {
        case Left(err)  => Left(err)
        case Right(key) => Right(FreqSample(key, new String(kv.value)))
      }
    }
  }
}
