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
import fs2.{Chunk, Pipe, Stream}
import cats.implicits._
import com.github.blemale.scaffeine.{Cache, Scaffeine}

import scala.concurrent.duration._

case class FeatureValueFlow(
    mapping: FeatureMapping,
    store: Persistence,
    updated: Cache[Key, Timestamp]
) extends Logging {
  def process: Pipe[IO, Event, List[FeatureValue]] = events =>
    events
      .evalMap(event => {
        mapping.features.map(_.writes(event, store)).sequence.map(_.flatten.toList)
      })
      .evalMapChunk(writes => {
        writes.map(write => commitWrite(write).map(_ => write)).sequence
      })
      .chunks
      .evalMap(chunk =>
        // syncState is a write-read barrier: makeValue reads back the state written by commitWrite above, and without
        // the barrier a pipelined redis backend can serve the read before the buffered write lands. One barrier per
        // chunk is enough as all commits of the chunk are done by this point.
        for {
          marked <- chunk.toList.traverse(_.traverse(w => shouldRefresh(w).map(_ -> w)))
          _      <- IO.whenA(marked.exists(_.exists(_._1)))(store.syncState)
          values <- marked.traverse(_.collect { case (true, w) => w }.traverse(makeValue).map(_.flatten))
        } yield Chunk.from(values)
      )
      .unchunks

  def commitWrite(write: Write): IO[Unit] = write match {
    case w: Put               => commitWrite(w, store.scalars.get(FeatureKey(w.key)))
    case w: PutTuple          => commitWrite(w, store.maps.get(FeatureKey(w.key)))
    case w: Increment         => commitWrite(w, store.counters.get(FeatureKey(w.key)))
    case w: PeriodicIncrement => commitWrite(w, store.periodicCounters.get(FeatureKey(w.key)))
    case w: Append            => commitWrite(w, store.lists.get(FeatureKey(w.key)))
    case w: PutStatSample     => commitWrite(w, store.stats.get(FeatureKey(w.key)))
    case w: PutFreqSample     => commitWrite(w, store.freqs.get(FeatureKey(w.key)))
  }

  private def commitWrite[W <: Write, F <: Feature[W, ?]](
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

  private def makeValue[W <: Write, F <: Feature[W, ? <: FeatureValue]](
      write: W,
      featureOption: Option[F]
  ): IO[Option[FeatureValue]] =
    featureOption match {
      case None          => IO.raiseError(new Exception(s"feature is not defined for write $write"))
      case Some(feature) => feature.computeValue(write.key, write.ts)
    }

}

object FeatureValueFlow {
  def apply(mapping: FeatureMapping, store: Persistence) = new FeatureValueFlow(
    mapping,
    store,
    Scaffeine().ticker(store.ticker).expireAfterAccess(1.hour).maximumSize(20000).weakValues().build[Key, Timestamp]()
  )
}
