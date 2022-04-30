package ai.metarank.source

import ai.metarank.config.EventSourceConfig
import ai.metarank.config.EventSourceConfig._
import ai.metarank.model.Event
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}

trait EventSource {
  def eventStream(env: StreamExecutionEnvironment, bounded: Boolean)(implicit
      ti: TypeInformation[Event]
  ): DataStream[Event]
}

object EventSource {
  def fromConfig(conf: EventSourceConfig): EventSource = conf match {
    case file: FileSourceConfig     => FileEventSource(file.path)
    case kafka: KafkaSourceConfig   => ???
    case pulsar: PulsarSourceConfig => ???
    case rest: RestSourceConfig     => RestApiSource(rest.host, rest.port)
  }
}
