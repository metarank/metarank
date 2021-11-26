package ai.metarank.flow

import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.scala.DataStream

object DataStreamOps {
  implicit class DataStreamGenericOps[T](stream: DataStream[T]) {
    def id(name: String) = stream.uid(name).name(name)

    def collect[R: TypeInformation](f: PartialFunction[T, R]): DataStream[R] =
      stream.flatMap(e => f.lift(e).toTraversable)

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
