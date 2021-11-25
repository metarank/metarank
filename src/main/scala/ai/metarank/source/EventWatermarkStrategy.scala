package ai.metarank.source

import EventWatermarkStrategy.EventTimeAssigner
import ai.metarank.model.Event
import org.apache.flink.api.common.eventtime.{
  BoundedOutOfOrdernessWatermarks,
  SerializableTimestampAssigner,
  TimestampAssigner,
  TimestampAssignerSupplier,
  WatermarkGenerator,
  WatermarkGeneratorSupplier,
  WatermarkStrategy
}

import java.time.Duration

case class EventWatermarkStrategy(maxOutOfOrder: Duration) extends WatermarkStrategy[Event] {
  override def createWatermarkGenerator(context: WatermarkGeneratorSupplier.Context): WatermarkGenerator[Event] =
    new BoundedOutOfOrdernessWatermarks[Event](maxOutOfOrder)

  override def createTimestampAssigner(context: TimestampAssignerSupplier.Context): TimestampAssigner[Event] =
    new EventTimeAssigner()
}

object EventWatermarkStrategy {
  def apply(jitterSeconds: Int = 20) = new EventWatermarkStrategy(Duration.ofSeconds(jitterSeconds))

  class EventTimeAssigner extends SerializableTimestampAssigner[Event] {
    override def extractTimestamp(element: Event, recordTimestamp: Long): Long = element.timestamp.ts
  }

}
