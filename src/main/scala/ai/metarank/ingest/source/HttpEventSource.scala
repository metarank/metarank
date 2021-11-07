package ai.metarank.ingest.source

import ai.metarank.config.IngestConfig.APIIngestConfig
import ai.metarank.ingest.source.HttpEventSource.RestSource
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

case class HttpEventSource(conf: APIIngestConfig)(implicit ti: TypeInformation[Event]) extends EventSource {
  override def source(env: StreamExecutionEnvironment): DataStream[Event] = {
    env.addSource(new RestSource(conf))
  }
}

object HttpEventSource {
  class RestSource(conf: APIIngestConfig) extends RichSourceFunction[Event] with Logging {
    var server: HttpServer                  = _
    var queue: ConcurrentLinkedQueue[Event] = _
    var shouldStopSignal                    = false

    override def open(parameters: Configuration): Unit = {
      queue = new ConcurrentLinkedQueue[Event]()
      server = ServerBootstrap
        .bootstrap()
        .setListenerPort(conf.port)
        .setExceptionListener(new ExceptionListener {
          override def onError(ex: Exception): Unit                             = ex.printStackTrace()
          override def onError(connection: HttpConnection, ex: Exception): Unit = ex.printStackTrace()
        })
        .register("*", new RequestHandler(queue))
        .create()
      server.start()
      logger.info(s"HTTP endpoint on port ${conf.port} is started")
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
              case Left(ex)     => logger.warn("cannot decode json", ex)
              case Right(value) => queue.addAll(value.asJavaCollection)
            }
          case Right(value) => queue.add(value)
        }
      }
    }
  }
}
