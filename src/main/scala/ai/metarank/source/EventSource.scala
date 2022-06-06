package ai.metarank.source

import ai.metarank.config.EventSourceConfig
import ai.metarank.config.EventSourceConfig._
import ai.metarank.model.Event
import org.apache.flink.api.common.typeinfo.TypeInformation
import io.findify.flink.api._

trait EventSource {
  def eventStream(env: StreamExecutionEnvironment, bounded: Boolean)(implicit
      ti: TypeInformation[Event]
  ): DataStream[Event]
}

object EventSource {
  def fromConfig(conf: EventSourceConfig)(implicit ti: TypeInformation[Event]): EventSource = conf match {
    case file: FileSourceConfig       => FileEventSource(file.path)
    case kafka: KafkaSourceConfig     => KafkaSource(kafka)
    case pulsar: PulsarSourceConfig   => PulsarEventSource(pulsar)
    case rest: RestSourceConfig       => RestApiEventSource(rest.host, rest.port)
    case kinesis: KinesisSourceConfig => KinesisSource(kinesis)
  }
}
