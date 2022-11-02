package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.model.Identifier.SessionId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scope.SessionScope
import ai.metarank.model.Write._
import ai.metarank.model.{Event, Feature, FeatureKey, FeatureValue, Key, Timestamp, Write}
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.{Pipe, Stream}
import cats.implicits._
import com.github.blemale.scaffeine.{Cache, Scaffeine}

import scala.concurrent.duration._

case class FeatureValueFlow(
    mapping: FeatureMapping,
    store: Persistence,
    updated: Cache[Key, Timestamp] = Scaffeine().expireAfterAccess(1.hour).build[Key, Timestamp]()
) extends Logging {
  def process: Pipe[IO, Event, List[FeatureValue]] = events =>
    events
      .evalMap(event => {
        mapping.features.map(_.writes(event)).sequence.map(_.flatten.toList)
      })
      .evalMapChunk(writes => {
        writes.map(write => commitWrite(write).map(_ => write)).sequence
      })
      .evalMap(writes =>
        writes
          .map(w =>
            shouldRefresh(w).flatMap {
              case true  => makeValue(w)
              case false => IO.pure(Nil)
            }
          )
          .sequence
          .map(_.flatten)
      )

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
        mapping.schema.configs.get(FeatureKey(write.key)) match {
          case Some(feature) =>
            IO {
              write.ts.diff(last) >= feature.refresh
            }
          case None => IO.raiseError(new Exception(s"feature ${write.key.feature} is not defined"))
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
