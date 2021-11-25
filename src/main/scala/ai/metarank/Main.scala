package ai.metarank

import ai.metarank.config.IngestConfig.{APIIngestConfig, FileIngestConfig}
import ai.metarank.config.{CmdLine, Config}
import ai.metarank.mode.train.Train
import ai.metarank.source.HttpEventSource
import cats.effect.{ExitCode, IO, IOApp}

//object Main extends IOApp {
//  override def run(args: List[String]): IO[ExitCode] = for {
//    cmdline <- CmdLine.parse(args)
//    config  <- Config.load(cmdline.config)
//    _ <- (cmdline.mode, config.ingest) match {
//      case ("inference", c: APIIngestConfig) =>
//        IngestMain.run(HttpEventSource(c), config)
//      case ("train", c: FileIngestConfig) => ???
//      case ("feature", _)                 => ???
//      case other                          => IO.raiseError(new IllegalArgumentException(s"mode $other is not supported"))
//    }
//  } yield {
//    ExitCode.Success
//  }
//}
