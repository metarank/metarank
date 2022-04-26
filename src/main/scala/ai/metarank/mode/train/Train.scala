package ai.metarank.mode.train

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.util.Logging
import better.files.File
import cats.effect.{ExitCode, IO, IOApp}
import io.circe.parser._
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor, Feature, Query}

import java.nio.charset.StandardCharsets
import scala.util.Random
import scala.collection.JavaConverters._

object Train extends IOApp with Logging {
  import ai.metarank.flow.DatasetSink.queryCodec
  override def run(args: List[String]): IO[ExitCode] = for {
    cmd     <- TrainCmdline.parse(args, System.getenv().asScala.toMap)
    config  <- Config.load(cmd.config)
    mapping <- IO { FeatureMapping.fromFeatureSchema(config.features, config.models) }
    ranker <- mapping.models.get(cmd.model) match {
      case Some(value) => IO.pure(value)
      case None        => IO.raiseError(new Exception(s"model ${cmd.model} is not configured"))
    }
    data <- loadData(cmd.input, ranker.datasetDescriptor)
    _    <- validate(data)
  } yield {
    val (train, test) = split(data, cmd.split)
    ranker.train(train, test, cmd.iterations) match {
      case Some(modelBytes) =>
        cmd.output.writeByteArray(modelBytes)
        logger.info(s"${cmd.model} model written to ${cmd.output}")
      case None =>
        logger.info(s"${cmd.model} model is empty")
    }
    ExitCode.Success
  }

  def loadData(path: File, desc: DatasetDescriptor) = {
    for {
      files <- IO { path.listRecursively().filter(_.extension(includeDot = false).contains("json")).toList }
      filesNel <- files match {
        case Nil =>
          IO.raiseError(
            new Exception("zero training files found. maybe you forgot to run bootstrap? or dir is incorrect?")
          )
        case nel => IO(logger.info(s"found training dataset files: $files")) *> IO.pure(nel)
      }
      queries <- IO {
        for {
          file  <- filesNel
          line  <- file.lineIterator(StandardCharsets.UTF_8)
          query <- decode[Query](line).toOption
        } yield {
          query
        }
      }
      queriesNel <- queries match {
        case Nil => IO.raiseError(new Exception("loaded 0 valid queries"))
        case nel => IO(s"loaded ${nel.size} queries") *> IO.pure(nel)
      }
    } yield { Dataset(desc, queriesNel) }
  }

  def split(dataset: Dataset, factor: Int) = {
    val (train, test) = dataset.groups.partition(_ => Random.nextInt(100) < factor)
    (Dataset(dataset.desc, train), Dataset(dataset.desc, test))
  }

  def validate(ds: Dataset): IO[Unit] = {
    if (ds.desc.features.isEmpty) {
      IO.raiseError(DatasetValidationError("No features configured"))
    } else if (ds.desc.features.size == 1) {
      IO.raiseError(DatasetValidationError("Only single ML feature defined"))
    } else if (ds.groups.isEmpty) {
      IO.raiseError(DatasetValidationError("No click-throughs loaded"))
    } else {
      IO.unit
    }
  }

  case class DatasetValidationError(msg: String) extends Exception(msg)
}
