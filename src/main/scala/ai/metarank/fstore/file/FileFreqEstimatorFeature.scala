package ai.metarank.fstore.file

import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.file.client.{FileClient, SortedDB}
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

case class FileFreqEstimatorFeature(config: FreqEstimatorConfig, db: SortedDB[String], format: StoreFormat)
    extends FreqEstimatorFeature {

  override def put(action: Write.PutFreqSample): IO[Unit] =
    IO.whenA(shouldSample(config.sampleRate))(for {
      key <- IO(format.key.encodeNoPrefix(action.key) + "/" + Random.nextInt(config.poolSize))
      _   <- IO(db.put(key, action.value))
    } yield {})

  override def computeValue(key: Key, ts: Timestamp): IO[Option[FeatureValue.FrequencyValue]] = for {
    kb      <- IO(format.key.encodeNoPrefix(key))
    values  <- IO(db.firstN(kb, config.poolSize))
    samples <- IO(values.map(_._2).toList)
  } yield {
    freqFromSamples(samples).map(freq => FrequencyValue(key, ts, freq, config.ttl))
  }
}

object FileFreqEstimatorFeature {
  case class FreqSample(key: Key, s: String)
  implicit val fileFreqSource: StateSource[FreqEstimatorState, FileFreqEstimatorFeature] =
    new StateSource[FreqEstimatorState, FileFreqEstimatorFeature] {
      override def source(f: FileFreqEstimatorFeature): Stream[IO, FreqEstimatorState] = {
        Stream
          .fromBlockingIterator[IO](f.db.all(), 128)
          .evalMap(kv => IO.fromEither(decode(f, kv)))
          .through(SortedGroupBy.groupBy[FreqSample, Key](_.key))
          .evalMap {
            case all @ head :: _ => IO.pure(FreqEstimatorState(head.key, all.map(_.s)))
            case Nil             => IO.raiseError(new Exception("empty batch"))
          }
      }
    }

  def decode(f: FileFreqEstimatorFeature, kv: (String, String)): Either[Throwable, FreqSample] = {
    val pos = kv._1.lastIndexOf('/')
    if (pos < 0) {
      Left(new Exception("cannot parse kv"))
    } else {
      val keyString = kv._1.substring(0, pos)
      f.format.key.decodeNoPrefix(keyString) match {
        case Left(err)  => Left(err)
        case Right(key) => Right(FreqSample(key, kv._2))
      }
    }
  }
}
