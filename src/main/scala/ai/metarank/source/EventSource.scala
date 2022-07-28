package ai.metarank.source

import ai.metarank.config.InputConfig
import ai.metarank.config.InputConfig._
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
  def fromConfig(conf: InputConfig)(implicit ti: TypeInformation[Event]): EventSource = conf match {
    case file: FileInputConfig       => FileEventSource(file)
    case kafka: KafkaInputConfig     => KafkaSource(kafka)
    case pulsar: PulsarInputConfig   => PulsarEventSource(pulsar)
    case rest: ApiInputConfig        => RestApiEventSource("none", 0)
    case kinesis: KinesisInputConfig => KinesisSource(kinesis)
  }
}
