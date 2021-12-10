package ai.metarank.source

import ai.metarank.model.Event
import better.files.File
import org.apache.flink.streaming.api.functions.source.SourceFunction
import io.circe.parser._
import io.circe.syntax._

case class LocalDirSource(path: String, limit: Long = Long.MaxValue) extends SourceFunction[Event] {
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
          decode[Event](file.contentAsString) match {
            case Left(value) =>
              throw value
            case Right(decoded) if count < limit =>
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
