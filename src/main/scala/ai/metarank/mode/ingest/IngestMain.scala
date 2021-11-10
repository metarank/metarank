package ai.metarank.mode.ingest

import ai.metarank.config.Config
import ai.metarank.feature.FeatureMapping
import ai.metarank.mode.ingest.source.EventSource
import ai.metarank.model.Event
import cats.effect.IO
import io.findify.featury.flink.Featury
import io.findify.featury.model.Schema
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.api.scala._
import scala.concurrent.duration._

object IngestMain {
  import ai.metarank.util.DataStreamOps._
  def run(source: EventSource, config: Config) = IO {
    val mapping       = FeatureMapping.fromFeatureSchema(config.feature)
    val featurySchema = Schema(mapping.features.flatMap(_.states))
    val streamEnv     = StreamExecutionEnvironment.getExecutionEnvironment
    streamEnv.setParallelism(1)
    streamEnv.setRuntimeMode(RuntimeExecutionMode.AUTOMATIC)
    val events: DataStream[Event] = source.eventStream(streamEnv)
    val writes                    = events.flatMap(e => mapping.features.flatMap(_.writes(e))).id("expand-writes")
    val updates                   = Featury.process(writes, featurySchema, 20.seconds).id("process-writes")
  }
}
