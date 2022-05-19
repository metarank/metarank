package ai.metarank.mode.upload

import ai.metarank.util.Logging
import io.findify.featury.model.Key.Tenant
import io.findify.featury.model.{FeatureValue, Timestamp}
import org.apache.flink.streaming.api.functions.{KeyedProcessFunction, ProcessFunction}
import org.apache.flink.util.Collector

import scala.collection.mutable
import scala.concurrent.duration._

case class WindowBatchFunction(period: FiniteDuration, size: Int)
    extends KeyedProcessFunction[Tenant, FeatureValue, List[FeatureValue]]
    with Logging {

  val buffer    = mutable.Buffer[FeatureValue]()
  var lastFlush = 0L
  var timerSet  = false

  override def processElement(
      value: FeatureValue,
      ctx: KeyedProcessFunction[Tenant, FeatureValue, List[FeatureValue]]#Context,
      out: Collector[List[FeatureValue]]
  ): Unit = {
    val now = ctx.timerService().currentProcessingTime()
    buffer.addOne(value)
    if ((buffer.length >= size) || (now - lastFlush >= period.toMillis)) {
      logger.info(s"flushed buffer of ${buffer.size} events (on size)")
      flush(now, out)
    }
    if (!timerSet && (period != 0.seconds)) {
      ctx.timerService().registerProcessingTimeTimer(now + period.toMillis)
      timerSet = true
    }
  }

  override def onTimer(
      timestamp: Long,
      ctx: KeyedProcessFunction[Tenant, FeatureValue, List[FeatureValue]]#OnTimerContext,
      out: Collector[List[FeatureValue]]
  ): Unit = {
    val now = ctx.timerService().currentProcessingTime()
    flush(now, out)
    logger.info(s"flushed buffer of ${buffer.size} events (on timer)")
    ctx.timerService().registerProcessingTimeTimer(now + period.toMillis)
  }

  override def close(): Unit = {
    logger.info(s"closed: buffer=${buffer.size}")
  }

  def flush(now: Long, out: Collector[List[FeatureValue]]) = {
    if (buffer.nonEmpty) {
      out.collect(buffer.toList)
      buffer.clear()
    }
    lastFlush = now
  }
}
