package ai.metarank.source

import ai.metarank.config.InputConfig
import ai.metarank.config.InputConfig._
import ai.metarank.model.Event
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.Stream

trait EventSource extends Logging {
  def conf: InputConfig
  def stream: Stream[IO, Event]
}

object EventSource {
  def fromConfig(conf: InputConfig): EventSource = conf match {
    case file: FileInputConfig       => FileEventSource(file)
    case kafka: KafkaInputConfig     => KafkaSource(kafka)
    case pulsar: PulsarInputConfig   => PulsarEventSource(pulsar)
    case kinesis: KinesisInputConfig => KinesisSource(kinesis)
  }
}
