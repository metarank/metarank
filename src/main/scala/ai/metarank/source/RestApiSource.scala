package ai.metarank.source

import ai.metarank.model.{Event, Field}
import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankingEvent, UserEvent}
import ai.metarank.source.RestApiSource.RestApiSourceFunction
import ai.metarank.util.Logging
import io.circe.parser.decode
import org.apache.commons.io.IOUtils
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.source.{RichSourceFunction, SourceFunction}
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}

import java.net.ConnectException
import java.nio.charset.StandardCharsets
import scala.util.{Failure, Success, Try}

case class RestApiSource(host: String, port: Int, limit: Long = Long.MaxValue) extends EventSource {
  override def eventStream(env: StreamExecutionEnvironment)(implicit ti: TypeInformation[Event]): DataStream[Event] =
    env.addSource(RestApiSourceFunction(host, port, limit))
}

object RestApiSource extends {
  case class RestApiSourceFunction(host: String, port: Int, limit: Long = Long.MaxValue)
      extends RichSourceFunction[Event]
      with Logging {
    @transient var stop                        = false
    @transient var count                       = 0
    @transient var client: CloseableHttpClient = _

    override def open(parameters: Configuration): Unit = {
      logger.info(s"created polling HTTP client for endpoint $host:$port")
      client = HttpClients.createDefault()
    }

    override def cancel(): Unit = {
      stop = true
    }

    override def close(): Unit = {
      client.close()
    }

    override def run(ctx: SourceFunction.SourceContext[Event]): Unit = {
      while (!stop) {
        val request = new HttpGet(s"http://$host:$port/feedback")
        Try(client.execute(request)) match {
          case Failure(e) =>
            logger.warn(s"cannot connect: ${e.getMessage}")
            ctx.markAsTemporarilyIdle()
            Thread.sleep(100)

          case Success(response) =>
            if (response.getStatusLine.getStatusCode == 200) {
              val json = IOUtils.toString(response.getEntity.getContent, StandardCharsets.UTF_8)
              decode[Event](json) match {
                case Left(value) =>
                  logger.error(s"cannot decode JSON message: '$json'", value)
                case Right(decoded) if count < limit =>
                  if (logger.isDebugEnabled) {
                    val eventString = decoded match {
                      case m: UserEvent =>
                        s"user: id=${m.user.value} fields: ${m.fields.map(formatField).mkString(" ")}"
                      case m: ItemEvent =>
                        s"item: id=${m.item.value} fields: ${m.fields.map(formatField).mkString(" ")}"
                      case r: RankingEvent =>
                        s"ranking: user=${r.user.value} session=${r.session.value} fields: ${r.fields.map(formatField).mkString(" ")}"
                      case i: InteractionEvent =>
                        s"interaction: type=${i.`type`} rank=${i.ranking.value} user=${i.user.value} session=${i.session.value} fields: ${i.fields.map(formatField).mkString(" ")}"
                    }
                    logger.debug(s"received ${eventString}")
                  }
                  ctx.collect(decoded)
                  count += 1
                case _ =>
                  count += 1
              }

            } else {
              ctx.markAsTemporarilyIdle()
              Thread.sleep(100)
            }

        }
        if (count >= limit) stop = true
      }
    }

    private def formatField(field: Field) = field match {
      case Field.StringField(name, value)     => s"$name=$value"
      case Field.BooleanField(name, value)    => s"$name=$value"
      case Field.NumberField(name, value)     => s"$name=$value"
      case Field.StringListField(name, value) => s"$name=${value.mkString(",")}"
      case Field.NumberListField(name, value) => s"$name=${value.mkString(",")}"
    }

  }

}
