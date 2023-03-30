package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.feature.FieldMatchFeature.FieldMatchSchema
import ai.metarank.feature.FieldMatchFeature.FieldMatcherType.BM25MatcherType
import ai.metarank.feature.matcher.BM25Matcher.TermFreqDic
import ai.metarank.fstore.TrainStore
import ai.metarank.main.CliArgs.{ExportArgs, TermFreqArgs}
import ai.metarank.model.Event.ItemEvent
import ai.metarank.model.{Event, Field, FieldName}
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.source.FileEventSource
import ai.metarank.util.{Logging, TextAnalyzer}
import cats.effect.IO
import cats.implicits._
import fs2.io.file.{Files, Path}
import fs2.{Chunk, Stream}
import io.circe.syntax._
import org.apache.commons.io.FileUtils

object TermFreq extends Logging {
  case class FieldLanguage(field: String, language: String)
  def run(args: TermFreqArgs): IO[Unit] = for {
    source <- IO(FileEventSource(FileInputConfig(args.data.toString)).stream)
    tf     <- processLanguage(args.language, args.fields.toSet, source)
    _      <- saveFile(args.out.toString, tf)
  } yield {
    logger.info("done")
  }

  def processLanguage(lang: String, fields: Set[String], events: Stream[IO, Event]): IO[TermFreqDic] = for {
    analyzer <- IO.fromEither(TextAnalyzer.create(lang))
    dic      <- TermFreqDic.fromEvents(events, fields, analyzer)
    _        <- info(s"built term-freq lang=$lang fields=[${fields.mkString(", ")}] terms=${dic.termfreq.size}")
  } yield {
    dic
  }

  def saveFile(path: String, dic: TermFreqDic): IO[Unit] = for {
    fileExists <- Files[IO].exists(Path(path))
    _          <- IO.whenA(fileExists)(IO.raiseError(new Exception(s"a file $path already exists")))
    json       <- IO.blocking(dic.asJson.spaces2)
    _          <- info(s"writing $path, size=${FileUtils.byteCountToDisplaySize(json.length)}")
    _ <- Stream
      .chunk[IO, Byte](Chunk.array(json.getBytes()))
      .through(Files[IO].writeAll(Path(path)))
      .compile
      .drain
    _ <- info("done")
  } yield {}

}
