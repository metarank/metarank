package ai.metarank.source

import HttpEventSource.RestSource
import ai.metarank.model.Event
import ai.metarank.util.Logging
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.source.{RichSourceFunction, SourceFunction}
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.hc.core5.http.{ClassicHttpRequest, ClassicHttpResponse, ExceptionListener, HttpConnection}
import org.apache.hc.core5.http.impl.bootstrap.{HttpServer, ServerBootstrap}
import org.apache.hc.core5.http.io.HttpRequestHandler
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.protocol.HttpContext
import io.circe.parser._

import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.JavaConverters._
import ai.metarank.flow.DataStreamOps._

case class HttpEventSource(port: Int) extends EventSource {
  override def eventStream(env: StreamExecutionEnvironment)(implicit ti: TypeInformation[Event]): DataStream[Event] = {
    env.addSource(new RestSource(port)).id("http-source")
  }
}

object HttpEventSource {
  class RestSource(port: Int) extends RichSourceFunction[Event] with Logging {
    var server: HttpServer                  = _
    var queue: ConcurrentLinkedQueue[Event] = _
    var shouldStopSignal                    = false

    override def open(parameters: Configuration): Unit = {
      queue = new ConcurrentLinkedQueue[Event]()
      server = ServerBootstrap
        .bootstrap()
        .setListenerPort(port)
        .setExceptionListener(new ExceptionListener {
          override def onError(ex: Exception): Unit                             = ex.printStackTrace()
          override def onError(connection: HttpConnection, ex: Exception): Unit = ex.printStackTrace()
        })
        .register("*", new RequestHandler(queue))
        .create()
      server.start()
      logger.info(s"HTTP endpoint on port ${port} is started")
    }
    override def cancel(): Unit = {
      shouldStopSignal = true
      server.stop()
      logger.info("HTTP endpoint stopped")
    }
    override def run(ctx: SourceFunction.SourceContext[Event]): Unit = {
      while (!shouldStopSignal) {
        val next = queue.poll()
        if (next != null) ctx.collect(next)
      }
    }

  }

  class RequestHandler(queue: ConcurrentLinkedQueue[Event]) extends HttpRequestHandler with Logging {
    override def handle(request: ClassicHttpRequest, response: ClassicHttpResponse, context: HttpContext): Unit = {
      response.setCode(200)
      for {
        entity <- Option(request.getEntity)
        bytes = EntityUtils.toString(entity)
      } {
        decode[Event](bytes) match {
          case Left(_) =>
            decode[List[Event]](bytes) match {
              case Left(ex) => logger.warn("cannot decode json", ex)
              case Right(value) =>
                logger.info(s"accepted request for $value")
                queue.addAll(value.asJavaCollection)
            }
          case Right(value) =>
            logger.info(s"accepted request for $value")
            queue.add(value)
        }
      }
    }
  }
}
