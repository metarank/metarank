package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.fstore.Persistence
import ai.metarank.model.Write._
import ai.metarank.model.{Env, Event, Feature, FeatureKey, FeatureValue, Key, Timestamp, Write}
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.{Pipe, Stream}
import cats.implicits._
import com.github.blemale.scaffeine.{Cache, Scaffeine}

import scala.concurrent.duration._

case class FeatureValueFlow(
    mappings: Map[Env, FeatureMapping],
    store: Persistence,
    updated: Cache[Key, Timestamp] = Scaffeine().expireAfterAccess(1.hour).build[Key, Timestamp]()
) extends Logging {
  def process: Pipe[IO, Event, FeatureValue] = events =>
    events
      .flatMap(event =>
        mappings.get(event.env) match {
          case Some(mapping) =>
            Stream.evalSeq(mapping.features.map(_.writes(event, store)).sequence.map(_.flatten.toList))
          case None =>
            Stream.raiseError[IO](new Exception(s"event ${event.id.value} uses undefined env ${event.env.value}"))
        }
      )
      .evalTap(commitWrite)
      .evalFilter(shouldRefresh)
      .evalMap(makeValue)
      .flatMap(Stream.fromOption(_))

  def commitWrite(write: Write): IO[Unit] = write match {
    case w: Put               => commitWrite(w, store.scalars.get(FeatureKey(w.key)))
    case w: PutTuple          => commitWrite(w, store.maps.get(FeatureKey(w.key)))
    case w: Increment         => commitWrite(w, store.counters.get(FeatureKey(w.key)))
    case w: PeriodicIncrement => commitWrite(w, store.periodicCounters.get(FeatureKey(w.key)))
    case w: Append            => commitWrite(w, store.lists.get(FeatureKey(w.key)))
    case w: PutStatSample     => commitWrite(w, store.stats.get(FeatureKey(w.key)))
    case w: PutFreqSample     => commitWrite(w, store.freqs.get(FeatureKey(w.key)))
  }

  private def commitWrite[W <: Write, F <: Feature[W, _]](
      write: W,
      featureOption: Option[F]
  ): IO[Unit] = {
    featureOption match {
      case None          => IO.raiseError(new Exception(s"feature is not defined for write $write"))
      case Some(feature) => feature.put(write)
    }
  }

  def shouldRefresh(write: Write) = {
    updated.getIfPresent(write.key) match {
      case None =>
        IO {
          updated.put(write.key, write.ts)
          true
        }
      case Some(last) =>
        mappings.get(write.key.scope.env).flatMap(mapping => mapping.schema.configs.get(FeatureKey(write.key))) match {
          case Some(feature) => IO(write.ts.diff(last) > feature.refresh)
          case None          => IO.raiseError(new Exception(s"feature ${write.key.feature} is not defined"))
        }
    }
  }

  def makeValue(write: Write): IO[Option[FeatureValue]] = {
    write match {
      case w: Put               => makeValue(w, store.scalars.get(FeatureKey(w.key)))
      case w: PutTuple          => makeValue(w, store.maps.get(FeatureKey(w.key)))
      case w: Increment         => makeValue(w, store.counters.get(FeatureKey(w.key)))
      case w: PeriodicIncrement => makeValue(w, store.periodicCounters.get(FeatureKey(w.key)))
      case w: Append            => makeValue(w, store.lists.get(FeatureKey(w.key)))
      case w: PutStatSample     => makeValue(w, store.stats.get(FeatureKey(w.key)))
      case w: PutFreqSample     => makeValue(w, store.freqs.get(FeatureKey(w.key)))
    }
  }

  private def makeValue[W <: Write, F <: Feature[W, _ <: FeatureValue]](
      write: W,
      featureOption: Option[F]
  ): IO[Option[FeatureValue]] =
    featureOption match {
      case None          => IO.raiseError(new Exception(s"feature is not defined for write $write"))
      case Some(feature) => feature.computeValue(write.key, write.ts)
    }

}
