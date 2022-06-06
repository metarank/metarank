package ai.metarank.source

import ai.metarank.config.EventSourceConfig
import ai.metarank.config.EventSourceConfig._
import ai.metarank.model.Event
import ai.metarank.util.Logging
import org.apache.flink.api.common.typeinfo.TypeInformation
import io.findify.flink.api._

import java.util.Properties

trait EventSource extends Logging {
  def eventStream(env: StreamExecutionEnvironment, bounded: Boolean)(implicit
      ti: TypeInformation[Event]
  ): DataStream[Event]

  protected def customProperties(options: Option[Map[String, String]]): Properties = {
    val props = new Properties()
    options match {
      case Some(value) =>
        value.foreach { case (key, value) =>
          logger.info(s"Kafka option override: '$key' = '$value'")
          props.put(key, value)
        }
      case None => // nothing
    }
    props
  }
}

object EventSource {
  def fromConfig(conf: EventSourceConfig)(implicit ti: TypeInformation[Event]): EventSource = conf match {
    case file: FileSourceConfig     => FileEventSource(file.path)
    case kafka: KafkaSourceConfig   => KafkaSource(kafka)
    case pulsar: PulsarSourceConfig => PulsarEventSource(pulsar)
    case rest: RestSourceConfig     => RestApiEventSource(rest.host, rest.port)
  }
}
