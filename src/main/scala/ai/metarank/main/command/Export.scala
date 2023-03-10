package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.BoosterConfig.{LightGBMConfig, XGBoostConfig}
import ai.metarank.config.Config
import ai.metarank.fstore.TrainStore
import ai.metarank.main.CliArgs.ExportArgs
import ai.metarank.main.command.train.SplitStrategy
import ai.metarank.main.command.util.StreamResource
import ai.metarank.ml.rank.LambdaMARTRanker.LambdaMARTPredictor
import ai.metarank.model.BoosterConfigFile.{LightGBMConfigFile, XGBoostConfigFile}
import ai.metarank.util.Logging
import cats.effect.{ExitCode, IO}
import cats.effect.kernel.Resource
import io.github.metarank.ltrlib.model.{Dataset, Feature}
import io.github.metarank.ltrlib.output.{CSVOutputFormat, LibSVMOutputFormat}

import java.nio.file.{Path, Paths}

object Export extends Logging {
  def run(
           conf: Config,
           ctsResource: Resource[IO, TrainStore],
           mapping: FeatureMapping,
           args: ExportArgs
  ): IO[Unit] = ctsResource.use(cts => doexport(cts, mapping, args.model, args.out, args.sample, args.split))

  def doexport(
                cts: TrainStore,
                mapping: FeatureMapping,
                modelName: String,
                out: Path,
                sample: Double,
                splitter: SplitStrategy
  ) = for {
    pred <- IO.fromOption(mapping.models.get(modelName))(new Exception(s"model $modelName is not defined in config"))
    lmart <- pred match {
      case lm: LambdaMARTPredictor => IO.pure(lm)
      case _ => IO.raiseError(new Exception(s"don't know how to export dataset for model $modelName"))
    }
    data    <- lmart.loadDataset(cts.getall())
    dataset <- lmart.splitDataset(splitter, lmart.desc, data)
    _ <- lmart.config.backend match {
      case c: LightGBMConfig => exportLightgbm(out, dataset.train, dataset.test, c)
      case c: XGBoostConfig  => exportXgboost(out, dataset.train, dataset.test, c)
    }
  } yield {
    logger.info("export done")
  }

  def exportXgboost(out: Path, train: Dataset, test: Dataset, model: XGBoostConfig) = for {
    _ <- info("using LibSVM format for XGBoost dataset export")
    _ <- StreamResource
      .of(Paths.get(out.toString + "/train.svm"))
      .use(s => IO(LibSVMOutputFormat.write(s, train)))
    _ <- StreamResource
      .of(Paths.get(out.toString + "/test.svm"))
      .use(s => IO(LibSVMOutputFormat.write(s, test)))
    _ <- StreamResource
      .of(Paths.get(out.toString + "/xgboost.conf"))
      .use(s => IO(XGBoostConfigFile(model, "train.svm", "test.svm").write(s)))
  } yield {}

  def exportLightgbm(out: Path, train: Dataset, test: Dataset, model: LightGBMConfig) = for {
    _    <- info("using CSV format for LightGBM dataset export")
    cats <- IO(train.desc.features.collect { case Feature.CategoryFeature(name) => name })
    _ <- StreamResource
      .of(Paths.get(out.toString + "/train.csv"))
      .use(s => IO(CSVOutputFormat.write(s, train, header = true)))
    _ <- StreamResource
      .of(Paths.get(out.toString + "/test.csv"))
      .use(s => IO(CSVOutputFormat.write(s, test, header = true)))
    _ <- StreamResource
      .of(Paths.get(out.toString + "/lightgbm.conf"))
      .use(s => IO(LightGBMConfigFile(model, "train.csv", "test.csv", cats).write(s)))
  } yield {}
}
