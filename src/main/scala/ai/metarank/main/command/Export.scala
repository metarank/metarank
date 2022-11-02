package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.fstore.ClickthroughStore
import ai.metarank.main.CliArgs.ExportArgs
import ai.metarank.main.command.util.StreamResource
import ai.metarank.rank.{LambdaMARTModel, NoopModel, ShuffleModel}
import ai.metarank.util.Logging
import cats.effect.{ExitCode, IO}
import cats.effect.kernel.Resource
import io.github.metarank.ltrlib.output.CSVOutputFormat

import java.nio.file.{Path, Paths}

object Export extends Logging {
  def run(
      conf: Config,
      ctsResource: Resource[IO, ClickthroughStore],
      mapping: FeatureMapping,
      args: ExportArgs
  ): IO[Unit] = ctsResource.use(cts => doexport(cts, mapping, args.model, args.out, args.sample))

  def doexport(cts: ClickthroughStore, mapping: FeatureMapping, model: String, out: Path, sample: Double) = for {
    modelConf <- IO
      .fromOption(mapping.models.get(model))(new Exception(s"model $model is not defined in config"))
    model <- modelConf match {
      case lm: LambdaMARTModel => IO.pure(lm)
      case _                   => IO.raiseError(new Exception(s"don't know how to export dataset for model $model"))
    }
    dataset <- Train.loadDataset(cts, model, sample)
    (train, test) = dataset
    _ <- StreamResource.of(Paths.get(out.toString + "/train.csv")).use(s => IO(CSVOutputFormat.write(s, train)))
    _ <- StreamResource.of(Paths.get(out.toString + "/test.csv")).use(s => IO(CSVOutputFormat.write(s, test)))
  } yield {
    logger.info("export done")
  }

}
