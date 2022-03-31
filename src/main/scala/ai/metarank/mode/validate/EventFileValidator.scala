package ai.metarank.mode.validate

import ai.metarank.mode.validate.CheckResult.{FailedCheck, SuccessfulCheck}
import ai.metarank.model.Event
import ai.metarank.model.Event.{InteractionEvent, MetadataEvent, RankingEvent}
import ai.metarank.util.Logging
import better.files.File
import org.apache.commons.io.IOUtils
import io.circe.parser._

import scala.collection.JavaConverters._
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

object EventFileValidator extends Logging {
  def check(file: File): CheckResult = {
    file.extension(includeDot = false) match {
      case Some("gz") | Some("gzip") =>
        logger.info("GZip compression detected")
        val lines =
          IOUtils.lineIterator(new GZIPInputStream(file.newFileInputStream), StandardCharsets.UTF_8).asScala.toList
        checkContents(lines)
      case Some("json") | Some("jsonl") =>
        logger.info("No compression detected")
        val lines = file.lineIterator.toList
        checkContents(lines)
      case other => FailedCheck(s"content type $other is not supported")
    }
  }

  def checkContents(lines: List[String]): CheckResult = {
    val parsed = lines.map(line => decode[Event](line))
    val metadata = parsed.collect { case Right(m @ MetadataEvent(_, _, _, _, _)) =>
      m
    }
    val ints = parsed.collect { case Right(i: InteractionEvent) =>
      i
    }
    val rankings = parsed.collect { case Right(r: RankingEvent) => r }
    val failed   = parsed.collect { case Left(x) => x }
    logger.info(s"total events: ${parsed.size}")
    logger.info(s"metadata events: ${metadata.size}")
    logger.info(s"interaction events: ${ints.size}")
    logger.info(s"ranking events: ${rankings.size}")
    logger.info(s"failed parsing events: ${failed.size}")
    if (metadata.nonEmpty && rankings.nonEmpty && ints.nonEmpty && failed.isEmpty) {
      SuccessfulCheck
    } else {
      FailedCheck("Problems with event consistency")
    }
  }
}
