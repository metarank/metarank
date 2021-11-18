package ai.metarank.util

import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.scala.DataStream

object DataStreamOps {
  implicit class DataStreamGenericOps[T](stream: DataStream[T]) {
    def id(name: String) =
      stream.uid(name).name(name)
  }
  implicit class DataStreamSinkOps[T](stream: DataStreamSink[T]) {
    def id(name: String) =
      stream.uid(name).name(name)
  }
}
