package ai.metarank.mode.validate

import ai.metarank.mode.validate.CheckResult.{FailedCheck, SuccessfulCheck}
import ai.metarank.model.Event
import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankingEvent}
import ai.metarank.util.Logging
import better.files.File
import org.apache.commons.io.IOUtils
import io.circe.parser._
import io.findify.featury.model.Timestamp

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
    val metadata = parsed.collect { case Right(m @ ItemEvent(_, _, _, _, _)) =>
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
    logger.info(s"failed parsing events: ${failed.size} (should be 0)")
    checkItems(metadata, ints, rankings)
    checkUsers(ints, rankings)
    checkClickthroughs(ints, rankings)
    checkMetadata(metadata, ints, rankings)
    if (metadata.nonEmpty && rankings.nonEmpty && ints.nonEmpty && failed.isEmpty) {
      SuccessfulCheck
    } else {
      FailedCheck("Problems with event consistency")
    }
  }

  def checkItems(metadata: List[ItemEvent], ints: List[InteractionEvent], rankings: List[RankingEvent]) = {
    val metadataItems    = metadata.map(_.item.value).toSet
    val interactionItems = ints.map(_.item.value).toSet
    val rankItems        = rankings.flatMap(_.items.toList.map(_.id.value)).toSet
    logger.info(
      s"unique items: metadata=${metadataItems.size} interaction=${interactionItems.size} ranking=${rankItems.size}"
    )
    logger.info(s"interaction items with no metadata: ${interactionItems
        .count(x => !metadataItems.contains(x))} (should be less than ${interactionItems.size})")
    logger.info(
      s"ranking items with no metadata: ${rankItems.count(x => !metadataItems.contains(x))} (should be less than ${rankItems.size})"
    )
  }

  def checkUsers(ints: List[InteractionEvent], rankings: List[RankingEvent]) = {
    val interactionUsers = ints.map(_.user.value).toSet
    val rankingUsers     = rankings.map(_.user.value).toSet
    logger.info(s"users: interaction=${interactionUsers.size} ranking=${rankingUsers.size}")
    logger.info(s"users with no ranking: ${interactionUsers.count(x => !rankingUsers.contains(x))} (should be 0)")
  }

  def checkClickthroughs(ints: List[InteractionEvent], rankings: List[RankingEvent]) = {
    val interactionIds = ints.map(_.ranking.value).toSet
    val rankingIds     = rankings.map(_.id.value).toSet
    logger.info(s"interactions with no ranking: ${interactionIds.count(x => !rankingIds.contains(x))} (should be 0)")
    val rankingMap = rankings.map(r => r.id.value -> r).toMap
    val interactionAfter = for {
      interaction <- ints
      ranking     <- rankingMap.get(interaction.ranking.value) if (interaction.timestamp.isAfter(ranking.timestamp))
    } yield {
      interaction
    }
    logger.info(s"out-of-order interactions: ${ints.size - interactionAfter.size} (should be 0)")
  }

  def checkMetadata(metadata: List[ItemEvent], ints: List[InteractionEvent], rankings: List[RankingEvent]) = {
    val firstUpdate = metadata
      .groupBy(_.item.value)
      .map { case (item, values) =>
        item -> values.map(_.timestamp).minBy(_.ts)
      }
    val oooInteractions = for {
      int <- ints
      ts  <- firstUpdate.get(int.item.value) if int.timestamp.isBefore(ts)
    } yield {
      int
    }
    logger.info(s"interactions happened before metadata: ${oooInteractions.size} (should be 0 or small number)")
  }

}
