package ai.metarank.flow

import ai.metarank.fstore.Persistence
import ai.metarank.fstore.cache.{CachedKVStore, NegCachedKVStore}
import ai.metarank.fstore.file.{FileKVStore, FilePersistence}
import ai.metarank.fstore.memory.MemKVStore
import ai.metarank.fstore.redis.RedisKVStore
import ai.metarank.model.{Event, Timestamp}
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.Pipe

import java.lang.management.ManagementFactory
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

object PrintProgress extends Logging {
  case class ProgressPeriod(
      start: Timestamp = Timestamp.now,
      total: Int = 0,
      batchTotal: Int = 0,
      gc: Long = 0,
      evict: Long = 0L,
      hits: Long = 0L,
      requests: Long = 0L
  ) {
    def inc(events: Int, gctime: Long) =
      copy(total = total + events, batchTotal = batchTotal + events, gc = gctime)
  }

  def tap[T](store: Option[Persistence], suffix: String): Pipe[IO, T, T] = input =>
    input.scanChunks(ProgressPeriod()) {
      case (pp @ ProgressPeriod(start, total, batch, gc, evictLast, hitsLast, reqLast), next) =>
        val now = Timestamp.now
        if ((start.diff(now) > 1.second)) {
          val timeDiffSeconds = start.diff(now).toMillis / 1000.0
          val perf            = math.round(batch / timeDiffSeconds)
          val gcNow           = getGcTime()
          val gcPercentage    = trim(100.0 * (gcNow - gc).toDouble / start.diff(now).toMillis, 2)
          val (evict, hits, reqs, cache) = store match {
            case Some(f: FilePersistence) =>
              f.values match {
                case CachedKVStore(MemKVStore(cache), _) =>
                  val stat         = cache.stats()
                  val hitsNow      = stat.hitCount()
                  val reqsNow      = stat.requestCount()
                  val hr           = trim(100.0 * (hitsNow - hitsLast) / (reqsNow - reqLast), 2)
                  val size         = cache.estimatedSize()
                  val ev           = stat.evictionCount()
                  val evictPercent = trim(100.0 * (stat.evictionCount() - evictLast) / size, 2)
                  (ev, hitsNow, reqsNow, s"cache[hits=$hr% size=$size evicted=$evictPercent%]")
                case _ => (0L, 0L, 0L, "")
              }
            case _ => (0L, 0L, 0L, "")
          }
          val (heapRel, heapMax) = getHeapUsage()
          val heap               = trim(100.0 * heapRel, 2)
          val heapG              = trim(heapMax / (1024 * 1024 * 1024.0), 2)
          logger.info(
            s"processed ${total} $suffix, perf=${perf}rps GC=${gcPercentage}% heap=$heap%/${heapG}G $cache"
          )
          (
            pp.copy(start = now, batchTotal = 0, evict = evict, hits = hits, requests = reqs).inc(next.size, gcNow),
            next
          )
        } else {
          (pp.inc(next.size, getGcTime()), next)
        }
    }

  def getHeapUsage(): (Double, Double) = {
    ManagementFactory.getMemoryPoolMXBeans.asScala.toList
      .collectFirst {
        case x if x.getName == "G1 Old Gen" =>
          val usage = x.getUsage
          val rel   = usage.getUsed / usage.getMax.toDouble
          (rel, usage.getMax.toDouble)
      }
      .getOrElse(0.0 -> 0.0)
  }

  def getGcTime() = {
    val use = ManagementFactory.getMemoryPoolMXBeans.asScala.toList.map(x => x.getName -> x.getUsage)

    ManagementFactory.getGarbageCollectorMXBeans.asScala.toList
      .map(_.getCollectionTime)
      .foldLeft(0L)(_ + _)
  }

  def trim(value: Double, digits: Int): Double = {
    val divider = math.pow(10, digits)
    math.round(divider * value) / divider
  }
}
