package ai.metarank.mode.train

import ai.metarank.FeatureMapping
import ai.metarank.config.{Config, MPath}
import ai.metarank.mode.FileLoader
import ai.metarank.rank.{LambdaMARTModel, Model}
import ai.metarank.util.Logging
import better.files.File
import cats.effect.{ExitCode, IO, IOApp}
import io.circe.parser._
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor, Feature, Query}

import java.nio.charset.StandardCharsets
import scala.util.Random
import scala.jdk.CollectionConverters._

object Train extends IOApp with Logging {
  import ai.metarank.flow.DatasetSink.queryCodec
  override def run(args: List[String]): IO[ExitCode] =
    args match {
      case configPath :: modelName :: Nil =>
        for {
          env          <- IO { System.getenv().asScala.toMap }
          confContents <- FileLoader.read(MPath(configPath), env)
          config       <- Config.load(new String(confContents, StandardCharsets.UTF_8))
          mapping      <- IO { FeatureMapping.fromFeatureSchema(config.features, config.models) }
          ranker <- mapping.models.get(modelName) match {
            case Some(value: LambdaMARTModel) => IO.pure(value)
            case _                            => IO.raiseError(new Exception(s"model $modelName is not configured"))
          }
          data <- loadData(config.bootstrap.workdir, ranker.datasetDescriptor, modelName)
          _    <- train(data, ranker, ranker.conf.path, env)
        } yield {
          ExitCode.Success
        }
      case _ =>
        IO(logger.error("usage: metarank train <config path> <model name>")) *> IO.pure(ExitCode.Success)
    }

  def loadData(path: MPath, desc: DatasetDescriptor, modelName: String) = {
    for {
      dataPath <- path / s"dataset-$modelName" match {
        case MPath.S3Path(_, _)    => IO.raiseError(new Exception(s"training works yet only with local datasets"))
        case MPath.LocalPath(path) => IO(File(path))
      }
      files <- IO { dataPath.listRecursively().filter(_.extension(includeDot = false).contains("json")).toList }
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

  def train(data: Dataset, ranker: Model, path: MPath, env: Map[String, String]) = {
    val (train, test) = split(data, 80)
    ranker.train(train, test) match {
      case Some(modelBytes) =>
        FileLoader.write(path, env, modelBytes) *> IO(logger.info(s"model written to $path"))
      case None =>
        IO(logger.info("model is empty"))
    }

  }

  case class DatasetValidationError(msg: String) extends Exception(msg)
}
