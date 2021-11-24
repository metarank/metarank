package ai.metarank.util

import io.findify.featury.model.Write
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.scala.DataStream

object DataStreamOps {
  implicit class DataStreamGenericOps[T](stream: DataStream[T]) {
    def id(name: String) = stream.uid(name).name(name)
    def watermark(f: T => Long) = stream.assignTimestampsAndWatermarks(
      WatermarkStrategy
        .forMonotonousTimestamps()
        .withTimestampAssigner(new SerializableTimestampAssigner[T] {
          override def extractTimestamp(element: T, recordTimestamp: Long): Long = f(element)
        })
    )

  }
  implicit class DataStreamSinkOps[T](stream: DataStreamSink[T]) {
    def id(name: String) = stream.uid(name).name(name)
  }
}
