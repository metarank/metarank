package ai.metarank.mode.train

import ai.metarank.FeatureMapping
import ai.metarank.config.{Config, MPath}
import ai.metarank.mode.CliApp
import ai.metarank.rank.{LambdaMARTModel, Model}
import cats.implicits._
import cats.effect.{ExitCode, IO}
import io.circe.parser._
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor, Feature, Query}

import java.nio.charset.StandardCharsets
import scala.util.Random

object Train extends CliApp {
  import ai.metarank.flow.DatasetSink.queryCodec

  override def usage: String = "usage: metarank train <config path> <model name>"

  override def run(
      args: List[String],
      env: Map[String, String],
      config: Config,
      mapping: FeatureMapping
  ): IO[ExitCode] = for {
    modelName <- IO.fromOption(args.lift(1))(new IllegalArgumentException(s"invalid params, $usage"))
    ranker <- mapping.models.get(modelName) match {
      case Some(value: LambdaMARTModel) => IO.pure(value)
      case _                            => IO.raiseError(new Exception(s"model $modelName is not configured"))
    }
    // data <- loadData(config.bootstrap.workdir, ranker.datasetDescriptor, modelName, env)
    // _    <- train(data, ranker, ranker.conf.path, env)
  } yield {
    ExitCode.Success
  }

//  def loadData(path: MPath, desc: DatasetDescriptor, modelName: String, env: Map[String, String]) = {
//    for {
//      files <- FS.listRecursive(path / s"dataset-$modelName", env).map(_.filter(_.path.endsWith(".json")))
//      filesNel <- files match {
//        case Nil =>
//          IO.raiseError(
//            new Exception("zero training files found. maybe you forgot to run bootstrap? or dir is incorrect?")
//          )
//        case nel => IO(logger.info(s"found training dataset files: $files")) *> IO.pure(nel)
//      }
//      contents <- filesNel.map(file => IO(logger.info(s"reading $file")) *> FS.read(file, env)).sequence
//      queries <- IO {
//        for {
//          fileBytes <- contents
//          line      <- LineReader.lines(fileBytes)
//          query     <- decode[Query](line).toOption
//        } yield {
//          query
//        }
//      }
//      queriesNel <- queries match {
//        case Nil => IO.raiseError(new Exception("loaded 0 valid queries"))
//        case nel => IO(s"loaded ${nel.size} queries") *> IO.pure(nel)
//      }
//    } yield { Dataset(desc, queriesNel) }
//  }

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

//  def train(data: Dataset, ranker: Model, path: MPath, env: Map[String, String]) = {
//    val (train, test) = split(data, 80)
//    ranker.train(train, test) match {
//      case Some(modelBytes) =>
//        FS.write(path, modelBytes, env) *> IO(logger.info(s"model written to $path"))
//      case None =>
//        IO(logger.info("model is empty"))
//    }
//
//  }

  case class DatasetValidationError(msg: String) extends Exception(msg)
}
