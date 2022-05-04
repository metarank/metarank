package ai.metarank.source

import ai.metarank.model.{Event, Field}
import ai.metarank.source.RestApiEventSource.RestApiSource
import ai.metarank.source.rest.{
  RestEnumerator,
  RestSourceReader,
  RestSplit,
  RestSplitCheckpointSerializer,
  RestSplitSerializer
}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.connector.source.{
  Boundedness,
  Source,
  SourceReader,
  SourceReaderContext,
  SplitEnumerator,
  SplitEnumeratorContext
}
import org.apache.flink.core.io.SimpleVersionedSerializer
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}

import scala.collection.mutable

case class RestApiEventSource(host: String, port: Int, workers: Int = 1, limit: Option[Long] = None)
    extends EventSource {
  override def eventStream(env: StreamExecutionEnvironment, bounded: Boolean)(implicit
      ti: TypeInformation[Event]
  ): DataStream[Event] = {
    val lim = (bounded, limit) match {
      case (true, None)        => Some(Long.MaxValue)
      case (true, Some(value)) => Some(value)
      case (false, _)          => None
    }
    env.fromSource(RestApiSource(host, port, workers, lim), EventWatermarkStrategy(), "rest-source")
  }
}

object RestApiEventSource extends {

  case class RestApiSource(host: String, port: Int, workers: Int, limit: Option[Long])
      extends Source[Event, RestSplit, List[RestSplit]] {
    override def createEnumerator(
        enumContext: SplitEnumeratorContext[RestSplit]
    ): SplitEnumerator[RestSplit, List[RestSplit]] = {
      RestEnumerator(enumContext, workers, limit)
    }

    override def restoreEnumerator(
        enumContext: SplitEnumeratorContext[RestSplit],
        checkpoint: List[RestSplit]
    ): SplitEnumerator[RestSplit, List[RestSplit]] =
      RestEnumerator(enumContext, mutable.Queue(checkpoint: _*))

    override def getSplitSerializer: SimpleVersionedSerializer[RestSplit] = RestSplitSerializer

    override def getBoundedness: Boundedness = limit match {
      case Some(_) => Boundedness.BOUNDED
      case None    => Boundedness.CONTINUOUS_UNBOUNDED
    }

    override def createReader(readerContext: SourceReaderContext): SourceReader[Event, RestSplit] = {
      RestSourceReader(readerContext, host, port)
    }

    override def getEnumeratorCheckpointSerializer: SimpleVersionedSerializer[List[RestSplit]] =
      RestSplitCheckpointSerializer
  }

}
