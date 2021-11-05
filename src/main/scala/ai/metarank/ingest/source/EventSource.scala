package ai.metarank.ingest.source

import ai.metarank.model.Event
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}

trait EventSource {
  def source(env: StreamExecutionEnvironment): DataStream[Event]
}
