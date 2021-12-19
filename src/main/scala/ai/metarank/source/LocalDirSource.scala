package ai.metarank.source

import ai.metarank.model.{Event, Field}
import ai.metarank.model.Event.{InteractionEvent, MetadataEvent, RankingEvent}
import ai.metarank.util.Logging
import better.files.File
import org.apache.flink.streaming.api.functions.source.SourceFunction
import io.circe.parser._
import io.circe.syntax._

case class LocalDirSource(path: String, limit: Long = Long.MaxValue) extends SourceFunction[Event] with Logging {
  @transient var stop  = false
  @transient var count = 0
  override def run(ctx: SourceFunction.SourceContext[Event]): Unit = {
    val dir = File(path)
    while (!stop) {
      val list = dir.listRecursively.toList.sortBy(_.name.toInt)
      if (list.nonEmpty) {
        for {
          file <- list
        } {
          val json = file.contentAsString
          decode[Event](json) match {
            case Left(value) =>
              logger.error(s"cannot decode JSON message: $json", value)
              throw value
            case Right(decoded) if count < limit =>
              if (logger.isDebugEnabled) {
                val eventString = decoded match {
                  case m: MetadataEvent =>
                    s"metadata: id=${m.item.value} fields: ${m.fields.map(formatField).mkString(" ")}"
                  case r: RankingEvent =>
                    s"ranking: user=${r.user.value} session=${r.session.value} fields: ${r.fields.map(formatField).mkString(" ")}"
                  case i: InteractionEvent =>
                    s"interaction: type=${i.`type`} rank=${i.ranking.value} user=${i.user.value} session=${i.session.value} fields: ${i.fields.map(formatField).mkString(" ")}"
                }
                logger.debug(s"received ${eventString}")
              }
              file.delete()
              ctx.collect(decoded)
              count += 1
            case _ =>
              count += 1
          }
        }
      } else {
        ctx.markAsTemporarilyIdle()
        Thread.sleep(50)
      }
      if (count >= limit) stop = true
    }
  }

  override def cancel(): Unit = {
    stop = true
  }

  private def formatField(field: Field) = field match {
    case Field.StringField(name, value)     => s"$name=$value"
    case Field.BooleanField(name, value)    => s"$name=$value"
    case Field.NumberField(name, value)     => s"$name=$value"
    case Field.StringListField(name, value) => s"$name=${value.mkString(",")}"
    case Field.NumberListField(name, value) => s"$name=${value.mkString(",")}"
  }
}

object LocalDirSource {
  class LocalDirWriter(dir: File) {
    var count = 0
    def write(event: Event) = {
      dir.createChild(count.toString).write(event.asJson.noSpacesSortKeys)
      count += 1
    }
  }
}
