package ai.metarank.source.rest

import ai.metarank.model.{Event, Field}
import ai.metarank.util.Logging
import org.apache.commons.io.IOUtils
import org.apache.flink.api.connector.source.{ReaderOutput, SourceReader, SourceReaderContext}
import org.apache.flink.core.io.InputStatus
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import io.circe.parser._

import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._
import java.util
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

case class RestSourceReader(ctx: SourceReaderContext, host: String, port: Int)
    extends SourceReader[Event, RestSplit]
    with Logging {
  @transient var client: CloseableHttpClient     = _
  @transient var queue: mutable.Queue[RestSplit] = _

  override def pollNext(output: ReaderOutput[Event]): InputStatus = {
    if (queue.isEmpty) {
      logger.info("split queue empty, REST source reader finished")
      InputStatus.END_OF_INPUT
    } else {
      val split   = queue.dequeue()
      val request = new HttpGet(s"http://$host:$port/feedback")
      Try(client.execute(request)) match {
        case Failure(e) =>
          logger.warn(s"cannot connect: ${e.getMessage}")
          queue.enqueue(split)
          InputStatus.NOTHING_AVAILABLE

        case Success(response) =>
          response.getStatusLine.getStatusCode match {
            case 200 =>
              val json = IOUtils.toString(response.getEntity.getContent, StandardCharsets.UTF_8)
              decode[Event](json) match {
                case Left(value) =>
                  logger.error(s"cannot decode JSON message: '$json'", value)
                  queue.enqueue(split)
                  InputStatus.NOTHING_AVAILABLE

                case Right(decoded) if !split.isFinished =>
                  output.collect(decoded)
                  queue.enqueue(split.decrement(1))
                  InputStatus.MORE_AVAILABLE
                case Right(decoded) =>
                  output.collect(decoded)
                  logger.info(s"split $split is finished")
                  ctx.sendSplitRequest()
                  InputStatus.END_OF_INPUT
              }
            case 204 =>
              // no content
              queue.enqueue(split)
              InputStatus.NOTHING_AVAILABLE
            case _ =>
              logger.warn(s"REST source reader got non-200 response: ${response.getStatusLine}")
              queue.enqueue(split)
              InputStatus.NOTHING_AVAILABLE
          }
      }
    }
  }

  override def addSplits(splits: util.List[RestSplit]): Unit = {
    logger.info(s"received splits ${splits}")
    queue.enqueue(splits.asScala: _*)
  }

  override def close(): Unit = {
    client.close()
  }

  override def start(): Unit = {
    ctx.sendSplitRequest()
    logger.info(s"created polling HTTP client for endpoint $host:$port")
    client = HttpClients.createDefault()
    queue = mutable.Queue()
  }

  override def snapshotState(checkpointId: Long): util.List[RestSplit] = queue.toList.asJava

  override def notifyNoMoreSplits(): Unit = {}

  override def isAvailable: CompletableFuture[Void] = if (queue.nonEmpty) {
    val executor = CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
    CompletableFuture.supplyAsync(() => null, executor)
  } else {
    CompletableFuture.completedFuture(null)
  }
}
