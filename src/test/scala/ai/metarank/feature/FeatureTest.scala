package ai.metarank.feature

import ai.metarank.FeatureMapping
import ai.metarank.config.Config.InteractionConfig
import ai.metarank.feature.FeatureTest.FeaturyMock
import ai.metarank.flow.FieldStore.MapFieldStore
import ai.metarank.model.Event.RankingEvent
import ai.metarank.model.{Event, FeatureSchema, Field, FieldId, FieldUpdate, MValue}
import cats.data.NonEmptyList
import com.github.blemale.scaffeine.Scaffeine
import io.findify.featury.model.Feature._
import io.findify.featury.model.Write._
import io.findify.featury.model._
import io.findify.featury.state.mem._

import scala.collection.mutable

trait FeatureTest {
  def process(events: List[Event], schema: FeatureSchema, request: RankingEvent): List[List[MValue]] = {
    val mapping = FeatureMapping.fromFeatureSchema(
      schema = NonEmptyList.of(schema),
      interactions = NonEmptyList.of(InteractionConfig("click", 1.0))
    )
    val fieldUpdates = mutable.Map[FieldId, Field]()
    val featury      = FeaturyMock(mapping.schema)
    val features = for {
      event <- events
      fields = FieldUpdate.fromEvent(event)
      _      = fields.foreach(u => fieldUpdates.put(u.id, u.value))
      write   <- mapping.features.flatMap(feature => feature.writes(event, MapFieldStore(fieldUpdates.toMap)))
      feature <- featury.write(write)
    } yield {
      feature.key -> feature
    }

    mapping.features.map {
      case feature: BaseFeature.ItemFeature    => feature.values(request, features.toMap)
      case feature: BaseFeature.RankingFeature => List(feature.value(request, features.toMap))
    }
  }

}

object FeatureTest {

  case class FeaturyMock(
      counters: Map[FeatureKey, Counter],
      periodicCounters: Map[FeatureKey, PeriodicCounter],
      lists: Map[FeatureKey, BoundedList],
      freqs: Map[FeatureKey, FreqEstimator],
      scalars: Map[FeatureKey, ScalarFeature],
      stats: Map[FeatureKey, StatsEstimator],
      maps: Map[FeatureKey, MapFeature]
  ) {
    def write(write: Write): Option[FeatureValue] = write match {
      case w: Put               => writeOne(scalars, w)
      case w: PutTuple          => writeOne(maps, w)
      case w: Increment         => writeOne(counters, w)
      case w: PeriodicIncrement => writeOne(periodicCounters, w)
      case w: Append            => writeOne(lists, w)
      case w: PutStatSample     => writeOne(stats, w)
      case w: PutFreqSample     => writeOne(freqs, w)
    }
    private def writeOne[W <: Write, F <: FeatureValue](map: Map[FeatureKey, Feature[W, F, _, _]], w: W): Option[F] = {
      map
        .get(FeatureKey(w.key))
        .flatMap(f => {
          f.put(w)
          f.computeValue(w.key, w.ts)
        })
    }
  }

  object FeaturyMock {
    def apply(mapping: Schema): FeaturyMock = {
      FeaturyMock(
        counters = mapping.counters.map(kv => kv._1 -> MemCounter(kv._2, Scaffeine().build[Key, Long]())),
        periodicCounters = mapping.periodicCounters.map(kv =>
          kv._1 -> MemPeriodicCounter(kv._2, Scaffeine().build[Key, Map[Timestamp, Long]]())
        ),
        lists = mapping.lists.map(kv => kv._1 -> MemBoundedList(kv._2, Scaffeine().build[Key, List[TimeValue]]())),
        freqs = mapping.freqs.map(kv => kv._1 -> MemFreqEstimator(kv._2, Scaffeine().build[Key, List[String]]())),
        scalars = mapping.scalars.map(kv => kv._1 -> MemScalarFeature(kv._2, Scaffeine().build[Key, Scalar]())),
        stats = mapping.stats.map(kv => kv._1 -> MemStatsEstimator(kv._2, Scaffeine().build[Key, List[Double]]())),
        maps = mapping.maps.map(kv => kv._1 -> MemMapFeature(kv._2, Scaffeine().build[Key, Map[String, Scalar]]()))
      )
    }
  }
}
