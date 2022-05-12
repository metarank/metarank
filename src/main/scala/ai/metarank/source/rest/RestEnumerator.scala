package ai.metarank.source.rest

import ai.metarank.util.Logging
import org.apache.flink.api.connector.source.{SplitEnumerator, SplitEnumeratorContext}
import scala.jdk.CollectionConverters._
import java.util
import scala.collection.mutable

case class RestEnumerator(ctx: SplitEnumeratorContext[RestSplit], queue: mutable.Queue[RestSplit])
    extends SplitEnumerator[RestSplit, List[RestSplit]]
    with Logging {
  override def start(): Unit = {}

  override def handleSplitRequest(subtaskId: Int, requesterHostname: String): Unit = {
    if (queue.isEmpty) {
      logger.info("no more rest splits left in the queue")
      ctx.signalNoMoreSplits(subtaskId)
    } else {
      val next = queue.dequeue()
      logger.info(s"assigned split $next for subtask $subtaskId to node $requesterHostname")
      ctx.assignSplit(next, subtaskId)
    }
  }

  override def addSplitsBack(splits: util.List[RestSplit], subtaskId: Int): Unit = {
    logger.info(s"adding ${splits.size()} splits back to queue")
    queue.enqueueAll(splits.asScala)
  }

  override def snapshotState(checkpointId: Long): List[RestSplit] = queue.toList

  override def close(): Unit = {}

  override def addReader(subtaskId: Int): Unit = {}
}

object RestEnumerator extends Logging {
  def apply(ctx: SplitEnumeratorContext[RestSplit], workers: Int, limitOption: Option[Long]) = {
    val splits = for {
      index <- 0 until workers
    } yield {
      val limit = limitOption.map(limit => {
        val start = index * (limit / workers.toDouble)
        val end   = (index + 1) * (limit / workers.toDouble)
        math.round(end - start)
      })
      RestSplit(s"split_$index", limit)
    }
    logger.info(s"generated splits: ${splits.toList}")
    new RestEnumerator(ctx, mutable.Queue(splits: _*))
  }
}
