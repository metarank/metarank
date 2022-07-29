package ai.metarank.source

import ai.metarank.config.InputConfig
import ai.metarank.config.InputConfig._
import ai.metarank.model.Event
import ai.metarank.util.Logging
import cats.effect.IO
import org.apache.flink.api.common.typeinfo.TypeInformation
import fs2.Stream

import java.util.Properties

trait EventSource extends Logging {
  def stream: Stream[IO, Event]
}

object EventSource {
  def fromConfig(conf: InputConfig): EventSource = conf match {
    case file: FileInputConfig       => FileEventSource(file)
    case kafka: KafkaInputConfig     => KafkaSource(kafka)
    case pulsar: PulsarInputConfig   => PulsarEventSource(pulsar)
    case api: ApiInputConfig         => RestApiEventSource(null)
    case kinesis: KinesisInputConfig => KinesisSource(kinesis)
  }
}
