package ai.metarank.mode.train

import ai.metarank.mode.train.TrainCmdline.ModelType
import ai.metarank.mode.upload.UploadCmdline
import ai.metarank.mode.upload.UploadCmdline.logger
import ai.metarank.util.Logging
import better.files.File
import cats.effect.IO
import io.findify.featury.values.StoreCodec.{JsonCodec, ProtobufCodec}
import scopt.OParser

case class TrainCmdline(input: File, split: Int, iterations: Int, output: File, config: File, booster: ModelType)

object TrainCmdline extends Logging {
  sealed trait ModelType
  case object LambdaMARTLightGBM extends ModelType
  case object LambdaMARTXGBoost  extends ModelType

  val builder = OParser.builder[TrainCmdline]
  val parser = {
    import builder._
    OParser.sequence(
      programName("Train"),
      head("Metarank", "v0.x"),
      opt[String]("input")
        .text("path to /dataset directory after the bootstrap, like file:///tmp/data or s3://bucket/dir")
        .required()
        .action((m, cmd) => cmd.copy(input = File(m))),
      opt[Int]("split")
        .text("train/validation split in percent, default 80/20")
        .optional()
        .action((m, cmd) => cmd.copy(split = m)),
      opt[String]("model-file")
        .text("model output file")
        .optional()
        .action((m, cmd) => cmd.copy(output = File(m))),
      opt[Int]("iterations")
        .text("number of iterations for model training, default 200")
        .optional()
        .action((m, cmd) => cmd.copy(iterations = m)),
      opt[String]("config")
        .text("config file with feature definition")
        .required()
        .action((m, cmd) => cmd.copy(config = File(m))),
      opt[String]("model-type")
        .text("which model to train")
        .required()
        .action((m, cmd) =>
          m match {
            case "lambdamart-lightgbm" => cmd.copy(booster = LambdaMARTLightGBM)
            case "lambdamart-xgboost"  => cmd.copy(booster = LambdaMARTXGBoost)
          }
        )
    )
  }

  def parse(args: List[String]): IO[TrainCmdline] = for {
    cmd <- IO.fromOption(
      OParser.parse(
        parser,
        args,
        TrainCmdline(null, 80, 200, File("out.model"), File("config.yml"), LambdaMARTLightGBM)
      )
    )(
      new IllegalArgumentException("cannot parse cmdline")
    )
    _ <- IO(logger.info(s"Input dir: ${cmd.input}"))
    _ <- IO(logger.info(s"split: ${cmd.split}"))
    _ <- IO(logger.info(s"model out file: ${cmd.output}"))
    _ <- IO(logger.info(s"iterations: ${cmd.iterations}"))
  } yield {
    cmd
  }

}
