package ai.metarank.source

import ai.metarank.source.FeatureValueWatermarkStrategy.FeatureValueTimeAssigner
import io.findify.featury.model.FeatureValue
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

case class FeatureValueWatermarkStrategy(maxOutOfOrder: Duration) extends WatermarkStrategy[FeatureValue] {
  override def createWatermarkGenerator(context: WatermarkGeneratorSupplier.Context): WatermarkGenerator[FeatureValue] =
    new BoundedOutOfOrdernessWatermarks[FeatureValue](maxOutOfOrder)

  override def createTimestampAssigner(context: TimestampAssignerSupplier.Context): TimestampAssigner[FeatureValue] =
    new FeatureValueTimeAssigner()
}

object FeatureValueWatermarkStrategy {
  def apply(jitterSeconds: Int = 20) = new FeatureValueWatermarkStrategy(Duration.ofSeconds(jitterSeconds))

  class FeatureValueTimeAssigner extends SerializableTimestampAssigner[FeatureValue] {
    override def extractTimestamp(element: FeatureValue, recordTimestamp: Long): Long = element.ts.ts
  }

}
