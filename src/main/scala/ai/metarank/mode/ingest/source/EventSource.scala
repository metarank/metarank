package ai.metarank.mode.ingest.source

import ai.metarank.model.Event
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}

trait EventSource {
  def eventStream(env: StreamExecutionEnvironment)(implicit ti: TypeInformation[Event]): DataStream[Event]
}
